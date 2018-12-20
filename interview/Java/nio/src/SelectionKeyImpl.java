package sun.nio.ch;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectionKey;
import sun.nio.ch.SelChImpl;
import sun.nio.ch.SelectorImpl;

public class SelectionKeyImpl extends AbstractSelectionKey {
	final SelChImpl channel;
	public final SelectorImpl selector;
	private int index;
	private volatile int interestOps;
	private int readyOps;

	SelectionKeyImpl(SelChImpl selChImpl, SelectorImpl selectorImpl) {
		this.channel = selChImpl;
		this.selector = selectorImpl;
	}

	@Override
	public SelectableChannel channel() {
		return (SelectableChannel) ((Object) this.channel);
	}

	@Override
	public Selector selector() {
		return this.selector;
	}

	int getIndex() {
		return this.index;
	}

	void setIndex(int n) {
		this.index = n;
	}

	private void ensureValid() {
		if (!this.isValid()) {
			throw new CancelledKeyException();
		}
	}

	@Override
	public int interestOps() {
		this.ensureValid();
		return this.interestOps;
	}

	@Override
	public SelectionKey interestOps(int n) {
		this.ensureValid();
		return this.nioInterestOps(n);
	}

	@Override
	public int readyOps() {
		this.ensureValid();
		return this.readyOps;
	}

	public void nioReadyOps(int n) {
		this.readyOps = n;
	}

	public int nioReadyOps() {
		return this.readyOps;
	}

	public SelectionKey nioInterestOps(int n) {
		if ((n & ~this.channel().validOps()) != 0) {
			throw new IllegalArgumentException();
		}
		this.channel.translateAndSetInterestOps(n, this);
		this.interestOps = n;
		return this;
	}

	public int nioInterestOps() {
		return this.interestOps;
	}
}