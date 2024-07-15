package org.stringtemplate.v4;

import org.stringtemplate.v4.misc.ErrorManager;
import org.stringtemplate.v4.misc.ErrorType;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.*;

public class MultiValueAttribute {
    public void setFirstArgument(InstanceScope scope, ST st, Object attr,  Interpreter interpreter) {
        if ( !st.impl.hasFormalArgs ) {
            if ( st.impl.formalArguments==null ) {
                st.add(ST.IMPLICIT_ARG_NAME, attr);
                return;
            }
            // else fall thru to set locals[0]
        }
        if ( st.impl.formalArguments==null ) {
            interpreter.errMgr.runTimeError(interpreter, scope,
                ErrorType.ARGUMENT_COUNT_MISMATCH,
                1,
                st.impl.name,
                0);
            return;
        }
        st.locals[0] = attr;
    }

    public void addToList(InstanceScope scope, List<Object> list, Object o) {
        o = convertAnythingIteratableToIterator(scope, o);
        if ( o instanceof Iterator ) {
            // copy of elements into our temp list
            Iterator<?> it = (Iterator<?>)o;
            while (it.hasNext()) list.add(it.next());
        }
        else {
            list.add(o);
        }
    }

    /**
     * Return the first attribute if multi-valued, or the attribute itself if
     * single-valued.
     * <p>
     * interpreter method is used for rendering expressions of the form
     * {@code <names:first()>}.</p>
     */
    private Object first(InstanceScope scope, Object v) {
        if ( v==null ) return null;
        Object r = v;
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            Iterator<?> it = (Iterator<?>)v;
            if ( it.hasNext() ) {
                r = it.next();
            }
        }
        return r;
    }

    /**
     * Return the last attribute if multi-valued, or the attribute itself if
     * single-valued. Unless it's a {@link List} or array, interpreter is pretty slow
     * as it iterates until the last element.
     * <p>
     * interpreter method is used for rendering expressions of the form
     * {@code <names:last()>}.</p>
     */
    private Object last(InstanceScope scope, Object v) {
        if ( v==null ) return null;
        if ( v instanceof List ) return ((List<?>)v).get(((List<?>)v).size()-1);
        else if ( v.getClass().isArray() ) {
            return Array.get(v, Array.getLength(v) - 1);
        }
        Object last = v;
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            Iterator<?> it = (Iterator<?>)v;
            while ( it.hasNext() ) {
                last = it.next();
            }
        }
        return last;
    }

    /**
     * Return everything but the first attribute if multi-valued, or
     * {@code null} if single-valued.
     */
    private Object rest(InstanceScope scope, Object v) {
        if ( v == null ) return null;
        if ( v instanceof List ) { // optimize list case
            List<?> elems = (List<?>)v;
            if ( elems.size()<=1 ) return null;
            return elems.subList(1, elems.size());
        }
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            List<Object> a = new ArrayList<Object>();
            Iterator<?> it = (Iterator<?>)v;
            if ( !it.hasNext() ) return null; // if not even one value return null
            it.next(); // ignore first value
            while (it.hasNext()) {
                Object o = it.next();
                a.add(o);
            }
            return a;
        }
        return null;  // rest of single-valued attribute is null
    }

    /** Return all but the last element. <code>trunc(<i>x</i>)==null</code> if <code><i>x</i></code> is single-valued. */
    private Object trunc(InstanceScope scope, Object v) {
        if ( v ==null ) return null;
        if ( v instanceof List ) { // optimize list case
            List<?> elems = (List<?>)v;
            if ( elems.size()<=1 ) return null;
            return elems.subList(0, elems.size()-1);
        }
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            List<Object> a = new ArrayList<Object>();
            Iterator<?> it = (Iterator<?>) v;
            while (it.hasNext()) {
                Object o = it.next();
                if ( it.hasNext() ) a.add(o); // only add if not last one
            }
            return a;
        }
        return null; // trunc(x)==null when x single-valued attribute
    }

    /** Return a new list without {@code null} values. */
    private Object strip(InstanceScope scope, Object v) {
        if ( v ==null ) return null;
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            List<Object> a = new ArrayList<Object>();
            Iterator<?> it = (Iterator<?>) v;
            while (it.hasNext()) {
                Object o = it.next();
                if ( o!=null ) a.add(o);
            }
            return a;
        }
        return v; // strip(x)==x when x single-valued attribute
    }

    /**
     * Return a list with the same elements as {@code v} but in reverse order.
     * <p>
     * Note that {@code null} values are <i>not</i> stripped out; use
     * {@code reverse(strip(v))} to do that.</p>
     */
    private Object reverse(InstanceScope scope, Object v) {
        if ( v==null ) return null;
        v = convertAnythingIteratableToIterator(scope, v);
        if ( v instanceof Iterator ) {
            List<Object> a = new LinkedList<Object>();
            Iterator<?> it = (Iterator<?>)v;
            while (it.hasNext()) a.add(0, it.next());
            return a;
        }
        return v;
    }

    /**
     * Return the length of a multi-valued attribute or 1 if it is a single
     * attribute. If {@code v} is {@code null} return 0.
     * <p>
     * The implementation treats several common collections and arrays as
     * special cases for speed.</p>
     */
    private Object length(Object v) {
        if ( v == null) return 0;
        int i = 1;      // we have at least one of something. Iterator and arrays might be empty.
        if ( v instanceof Map ) i = ((Map<?, ?>)v).size();
        else if ( v instanceof Collection ) i = ((Collection<?>)v).size();
        else if ( v instanceof Object[] ) i = ((Object[])v).length;
        else if ( v.getClass().isArray() ) i = Array.getLength(v);
        else if ( v instanceof Iterable || v instanceof Iterator ) {
            Iterator<?> it = v instanceof Iterable ? ((Iterable<?>)v).iterator() : (Iterator<?>)v;
            i = 0;
            while ( it.hasNext() ) {
                it.next();
                i++;
            }
        }
        return i;
    }

    public String toString(STWriter out, InstanceScope scope, Object value,  Interpreter interpreter) {
        if ( value!=null ) {
            if ( value.getClass()==String.class ) return (String)value;
            // if not string already, must evaluate it
            StringWriter sw = new StringWriter();
            STWriter stw;
            try {
                Class<? extends STWriter> writerClass = out.getClass();
                Constructor<? extends STWriter> ctor = writerClass.getConstructor(Writer.class);
                stw = ctor.newInstance(sw);
            }
            catch (Exception e) {
                stw = new AutoIndentWriter(sw);
               interpreter.errMgr.runTimeError(interpreter, scope, ErrorType.WRITER_CTOR_ISSUE, out.getClass().getSimpleName());
            }

            if (interpreter.debug && !scope.earlyEval) {
                scope = new InstanceScope(scope, scope.st);
                scope.earlyEval = true;
            }

            interpreter.writeObjectNoOptions(stw, scope, value);

            return sw.toString();
        }
        return null;
    }
}
