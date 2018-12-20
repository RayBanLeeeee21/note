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
import sun.nio.ch.SelChImpl;
import sun.nio.ch.SelectionKeyImpl;
import sun.nio.ch.Util;

public abstract class SelectorImpl extends AbstractSelector {
	protected Set<SelectionKey> selectedKeys = new HashSet<SelectionKey>(); // 
	protected HashSet<SelectionKey> keys = new HashSet();
	private Set<SelectionKey> publicKeys;
	private Set<SelectionKey> publicSelectedKeys;

	protected SelectorImpl(SelectorProvider selectorProvider) {
		super(selectorProvider);
		if (Util.atBugLevel("1.4")) {
			this.publicKeys = this.keys;
			this.publicSelectedKeys = this.selectedKeys;
		} else {
			this.publicKeys = Collections.unmodifiableSet(this.keys);
			this.publicSelectedKeys = Util.ungrowableSet(this.selectedKeys);
		}
	}

	@Override
	public Set<SelectionKey> keys() {
		if (!this.isOpen() && !Util.atBugLevel("1.4")) {
			throw new ClosedSelectorException();
		}
		return this.publicKeys;
	}

	@Override
	public Set<SelectionKey> selectedKeys() {
		if (!this.isOpen() && !Util.atBugLevel("1.4")) {
			throw new ClosedSelectorException();
		}
		return this.publicSelectedKeys;
	}

	protected abstract int doSelect(long var1) throws IOException;

	private int lockAndDoSelect(long l) throws IOException {
		SelectorImpl selectorImpl = this;
		synchronized (selectorImpl) {
			if (!this.isOpen()) {
				throw new ClosedSelectorException();
			}
			Set<SelectionKey> set = this.publicKeys;
			synchronized (set) {
				Set<SelectionKey> set2 = this.publicSelectedKeys;
				synchronized (set2) {
					return this.doSelect(l);
				}
			}
		}
	}

	@Override
	public int select(long l) throws IOException {
		if (l < 0L) {
			throw new IllegalArgumentException("Negative timeout");
		}
		return this.lockAndDoSelect(l == 0L ? -1L : l);
	}

	@Override
	public int select() throws IOException {
		return this.select(0L);
	}

	@Override
	public int selectNow() throws IOException {
		return this.lockAndDoSelect(0L);
	}

	@Override
	public void implCloseSelector() throws IOException {
		this.wakeup();
		SelectorImpl selectorImpl = this;
		synchronized (selectorImpl) {
			Set<SelectionKey> set = this.publicKeys;
			synchronized (set) {
				Set<SelectionKey> set2 = this.publicSelectedKeys;
				synchronized (set2) {
					this.implClose();
				}
			}
		}
	}

	protected abstract void implClose() throws IOException;

	public void putEventOps(SelectionKeyImpl selectionKeyImpl, int n) {
	}

	@Override
	protected final SelectionKey register(AbstractSelectableChannel abstractSelectableChannel, int n, Object object) {
		if (!(abstractSelectableChannel instanceof SelChImpl)) {
			throw new IllegalSelectorException();
		}
		SelectionKeyImpl selectionKeyImpl = new SelectionKeyImpl((SelChImpl) ((Object) abstractSelectableChannel),
				this);
		selectionKeyImpl.attach(object);
		Set<SelectionKey> set = this.publicKeys;
		synchronized (set) {
			this.implRegister(selectionKeyImpl);
		}
		selectionKeyImpl.interestOps(n);
		return selectionKeyImpl;
	}

	protected abstract void implRegister(SelectionKeyImpl var1);

	void processDeregisterQueue() throws IOException {
		Set<SelectionKey> set;
		Set<SelectionKey> set2 = set = this.cancelledKeys();
		synchronized (set2) {
			if (!set.isEmpty()) {
				Iterator<SelectionKey> iterator = set.iterator();
				while (iterator.hasNext()) {
					SelectionKeyImpl selectionKeyImpl = (SelectionKeyImpl) iterator.next();
					try {
						this.implDereg(selectionKeyImpl);
					} catch (SocketException socketException) {
						throw new IOException("Error deregistering key", socketException);
					} finally {
						iterator.remove();
					}
				}
			}
		}
	}

	protected abstract void implDereg(SelectionKeyImpl var1) throws IOException;

	@Override
	public abstract Selector wakeup();
}