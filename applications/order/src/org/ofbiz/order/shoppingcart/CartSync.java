package org.ofbiz.order.shoppingcart;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;

/**
 * SCIPIO: Cart synchronization object; replaces synchronized blocks on a cart lock.
 * <p>
 * Use this for synchronizing cart-related operations that do not actually modify the cart itself.
 * If the operation modifies the cart, use {@link CartUpdate#updateSection(HttpServletRequest)} instead.
 * <p>
 * Basic usage:
 * <pre>{@code
 * try (CartSync cartSync = CartSync.synchronizedSection(request)) {
 *    // ...
 * }</pre>
 * <p>
 * NOTE: If used from pre-java 8 code, must be closed in a finally block.
 * <p>
 * Added 2018-11-29.
 *
 * @see CartUpdate
 */
@SuppressWarnings("serial")
public class CartSync implements AutoCloseable, Serializable {
    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    /**
     * Controls whether the CartSync is cloned every section or not.
     * <p>
     * It is not technically necessary if all calling code is correct, but because it's possible
     * for caller to accidentally forget to call {@link #clone()}, we need this to detect those cases
     * in {@link #finalize()}.
     */
    private static final boolean oneInstancePerSection = true;
    private static final Collection<String> excludeClassesLogCaller = UtilMisc.toSet(CartSync.class.getName(), CartUpdate.class.getName());

    /**
     * An implementation of CartSync that does nothing, mainly for code simplification.
     */
    public static final CartSync DUMMY = new DummyCartSync();

    private final ReentrantLock cartLock;
    // NOTE: This flag may evaluate only once per session (roughly), which should be OK
    protected final boolean debug = (ShoppingCart.DEBUG || Debug.verboseOn());

    // Following fields are only used if oneInstancePerSection==true
    private boolean endCalledOrOk = false;

    /**
     * Constructor for main lock instance (stored in session).
     */
    protected CartSync(ReentrantLock cartUpdateLock) {
        this.cartLock = cartUpdateLock;
        endCalledOrOk = true;
    }

    /**
     * Lock-sharing copy constructor.
     */
    protected CartSync(CartSync other) {
        this.cartLock = other.cartLock;
        endCalledOrOk = false;
    }

    /**
     * Returns a cart synchronization section. This takes the place of
     * a synchronized block and should be enclosed in a try-with-resources block.
     */
    public static CartSync synchronizedSection(HttpServletRequest request) {
        CartSync cartSync = getCartLockObject(request);
        // NOTE: we only need a local copy if verifying with finalize
        if (oneInstancePerSection) {
            cartSync = new CartSync(cartSync);
        }
        cartSync.begin();
        return cartSync;
    }

    protected void begin() {
        if (isDebug()) { // Usually not necessary to log this; covered by CartUpdate
            Debug.logInfo("Begin cart sync section" + getLogSuffix(), module);
        }
        cartLock.lock();
    }

    protected void end() {
        if (isDebug()) {
            Debug.logInfo("End cart sync section" + getLogSuffix(), module);
        }
        try {
            cartLock.unlock();
        } catch(Exception e) {
            Debug.logError(e, "CartSync: fatal: could not release cart lock - this should not happen"
                    + getLogSuffixDetailed(), module);
        }
        endCalledOrOk = true;
    }

    @Override
    public void close() {
        end();
    }

    /**
     * SCIPIO: Returns a lock which should be locked whenever modifying the cart.
     * NOTE: Client code should use {@link #synchronizedSection(HttpServletRequest)} instead.
      */
    public static CartSync getCartLockObject(HttpServletRequest request) {
        return getCartLockObject(request.getSession());
    }

    /**
     * SCIPIO: Returns a lock which should be locked whenever modifying the cart.
     * NOTE: Client code should use {@link #synchronizedSection(HttpServletRequest)} instead.
      */
    public static CartSync getCartLockObject(HttpSession session) {
        CartSync lock = (CartSync) session.getAttribute("shoppingCartLock");
        if (lock == null) {
            synchronized(UtilHttp.getSessionSyncObject(session)) {
                lock = (CartSync) session.getAttribute("shoppingCartLock");
                if (lock == null) {
                    if (ShoppingCart.verboseOn()) {
                        Debug.logInfo("shoppingCartLock not found in session; creating", module);
                    }
                    lock = createSetLockObject(session);
                }
            }
        }
        return lock;
    }

    /**
     * Creates a new CartSync instance with a new ReentrantLock and stores it in session.
     */
    public static CartSync createSetLockObject(HttpSession session) {
        CartSync lock = create();
        session.setAttribute("shoppingCartLock", lock);
        return lock;
    }

    /**
     * Creates a new CartSync instance with a new ReentrantLock.
     */
    public static CartSync create() {
        return new CartSync(new ReentrantLock());
    }

    /**
     * SCIPIO: Create a new lock object for {@link #setLockObject} and the
     * shoppingCartLock session attribute.
     */
    public static CartSync createLockObject() {
        return CartSync.create();
    }

    public boolean isDebug() {
        return debug;
    }

    String getLogSuffix() {
        if (isDebug()) {
            return getLogSuffixDetailed();
        } else {
            return "";
        }
    }

    String getLogSuffixDetailed() {
        return " (" + Debug.getCallerShortInfo(excludeClassesLogCaller) + ")";
    }

    @Override
    protected void finalize() throws Throwable {
        if (!endCalledOrOk) {
            Debug.logError("CartSync: finalize: fatal: cart sync section was never closed - this should"
                    + " not happen - close() must always be called, usually in a try/finally or try-with-resources block"
                    + getLogSuffixDetailed(), module);
            try {
                cartLock.unlock();
            } catch(Exception e) {
                Debug.logError(e, "CartSync: finalize: fatal: could not release cart lock; session cart may be ruined"
                        + getLogSuffixDetailed(), module);
            }
        }
    }

    private static class DummyCartSync extends CartSync {
        DummyCartSync() {
            super((ReentrantLock) null);
        }

        @Override
        protected void begin() {
            // do nothing
        }

        @Override
        protected void end() {
            // do nothing
        }
    }

}
