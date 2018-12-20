package sun.nio.ch;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class SelectorImpl extends AbstractSelector {
    protected Set<SelectionKey> selectedKeys;
    protected HashSet<SelectionKey> keys;
    private Set<SelectionKey> publicKeys;
    private Set<SelectionKey> publicSelectedKeys;

    protected SelectorImpl(SelectorProvider paramSelectorProvider) {
        super(paramSelectorProvider);
        keys = new HashSet();
        selectedKeys = new HashSet();
        if (Util.atBugLevel("1.4")) {
            publicKeys = keys;
            publicSelectedKeys = selectedKeys;
        } else {
            publicKeys = Collections.unmodifiableSet(keys);
            publicSelectedKeys = Util.ungrowableSet(selectedKeys);
        }
    }

    public Set<SelectionKey> keys() {
        if ((!isOpen()) && (!Util.atBugLevel("1.4"))) {
            throw new ClosedSelectorException();
        }
        return publicKeys;
    }

    public Set<SelectionKey> selectedKeys() {
        if ((!isOpen()) && (!Util.atBugLevel("1.4"))) {
            throw new ClosedSelectorException();
        }
        return publicSelectedKeys;
    }

    public int select(long paramLong) throws IOException {
        if (paramLong < 0L) {
            throw new IllegalArgumentException("Negative timeout");
        }
        return lockAndDoSelect(paramLong == 0L ? -1L : paramLong);
    }

    public int select() throws IOException {
        return select(0L);
    }

    public int selectNow() throws IOException {
        return lockAndDoSelect(0L);
    }

    public void implCloseSelector() throws IOException {
        wakeup();
        synchronized (this) {
            synchronized (publicKeys) {
                synchronized (publicSelectedKeys) {
                    implClose();
                }
            }
        }
    }

    protected final SelectionKey register(AbstractSelectableChannel paramAbstractSelectableChannel, int paramInt,
            Object paramObject) {
        if (!(paramAbstractSelectableChannel instanceof SelChImpl)) {
            throw new IllegalSelectorException();
        }
        SelectionKeyImpl localSelectionKeyImpl = new SelectionKeyImpl((SelChImpl) paramAbstractSelectableChannel, this);
        localSelectionKeyImpl.attach(paramObject);
        synchronized (publicKeys) {
            implRegister(localSelectionKeyImpl);
        }
        localSelectionKeyImpl.interestOps(paramInt);
        return localSelectionKeyImpl;
    }

    void processDeregisterQueue() throws IOException {
        Set localSet = cancelledKeys();
        synchronized (localSet) {
            if (!localSet.isEmpty()) {
                Iterator localIterator = localSet.iterator();
                while (localIterator.hasNext()) {
                    SelectionKeyImpl localSelectionKeyImpl = (SelectionKeyImpl) localIterator.next();
                    try {
                        implDereg(localSelectionKeyImpl);
                    } catch (SocketException localSocketException) {
                        throw new IOException("Error deregistering key", localSocketException);
                    } finally {
                        localIterator.remove();
                    }
                }
            }
        }
    }

    protected abstract void implDereg(SelectionKeyImpl paramSelectionKeyImpl) throws IOException;

    public abstract Selector wakeup();
}
