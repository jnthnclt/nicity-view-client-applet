package colt.nicity.view.client.applet;

import colt.nicity.core.collection.CArray;
import colt.nicity.core.lang.IOut;
import colt.nicity.core.lang.SysOut;
import colt.nicity.core.observer.AObservable;

public class RefreshStack extends AObservable implements IRefreshQueue {

    public RefreshStack() {}
    final private CArray<IRefresh> toRefresh = new CArray<IRefresh>(IRefresh.class);
    public IRefresh working;

    public IRefresh[] debugPeekAtStack() {
        return (IRefresh[])toRefresh.getAll();
    }

    public void purgeWork(boolean _stopRunning) {
        Object[] all = toRefresh.removeAll();
        if (isBeingObserved()) {
            for (Object a : all) {
                change(URefreshable.cModeDone, a);
            }
        }
        if (_stopRunning && running != null) {
            running.interrupt();
        }
    }

    public IOut wait(IRefresh _job) {
        return new SysOut();
    }

    Thread running;
    @Override
    public void addWork(IRefresh _refresh) {
        synchronized (toRefresh) {
            toRefresh.insertLast(_refresh);
            if (isBeingObserved()) {
                change(URefreshable.cModeAdd, _refresh);
            }
            if (working == null) {
                working = new IRefresh() {

                    private IRefresh work;

                    @Override
                    public void refresh(IOut _) {
                        while (true) {
                            work = getWork();
                            if (work != null) {
                                IOut wait = RefreshStack.this.wait(work);
                                if (isBeingObserved()) {
                                    change(URefreshable.cModeStarted, new Object[]{work, wait});
                                }
                                work.refresh(wait);
                                if (isBeingObserved()) {
                                    change(URefreshable.cModeDone, work);
                                }
                            }
                            synchronized (toRefresh) {
                                if (toRefresh.getCount() == 0) {
                                    working = null;
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public String toString() {
                        IRefresh _work = work;
                        if (_work != null) {
                            return _work.toString();
                        }
                        return "";
                    }
                };
                final IRefresh _working = working;
                running = new Thread("Refresh") {
                    @Override
                    public void run() {
                        _working.refresh(new SysOut());
                        synchronized (toRefresh) {
                            running = null;
                        }
                    }
                };
                running.start();
            }
        }
    }

    public IRefresh getWork() {
        synchronized (toRefresh) {
            IRefresh refresh = (IRefresh) toRefresh.removeFirst();
            return refresh;
        }
    }

    public int getCount() {
        return toRefresh.getCount();
    }

    @Override
    public String toString() {
        return "(" + getCount() + ")";
    }
}
