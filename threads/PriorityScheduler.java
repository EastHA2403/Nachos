package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

	public static void selfTest() {
		System.out.println("== 우선순위 스케줄러 테스트 ==");

		PriorityScheduler ps = new PriorityScheduler();
		ThreadQueue tq = ps.newThreadQueue(false); // priority transfer 안함

		// 테스트할 스레드들 만들기
		KThread thread1 = new KThread(new Runnable() {
			public void run() {
				System.out.println("스레드1 (우선순위=1) 실행됨");
			}
		}).setName("thread1");

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				System.out.println("스레드2 (우선순위=7) 실행됨");
			}
		}).setName("thread2");

		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				System.out.println("스레드3 (우선순위=4) 실행됨");
			}
		}).setName("thread3");

		KThread thread4 = new KThread(new Runnable() {
			public void run() {
				System.out.println("스레드4 (우선순위=5) 실행됨");
			}
		}).setName("thread4");

		KThread thread5 = new KThread(new Runnable() {
			public void run() {
				System.out.println("스레드5 (우선순위=5) 실행됨");
			}
		}).setName("thread5");


		boolean status = Machine.interrupt().disable();

		// 우선순위 설정해주기
		ps.setPriority(thread1, 1);
		ps.setPriority(thread2, 7);
		ps.setPriority(thread3, 4);
		ps.setPriority(thread4, 5);
		ps.setPriority(thread5, 5);

		// 큐에 넣기 (순서 중요함)
		tq.waitForAccess(thread1); // 우선순위 낮음
		tq.waitForAccess(thread4); // 5번 먼저
		tq.waitForAccess(thread2); // 우선순위 제일 높음
		tq.waitForAccess(thread5); // 5번 나중에
		tq.waitForAccess(thread3); // 중간

		Machine.interrupt().restore(status);

		// 우선순위 순서대로 실행하기
		KThread t;
		while ((t = tq.nextThread()) != null) {
			t.fork();
		}

		// 끝날때까지 기다리기
		thread1.join();
		thread2.join();
		thread3.join();
		thread4.join();
		thread5.join();

		System.out.println("== 테스트 끝 ==");
	}






	/**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}
	private LinkedList<ThreadState> waitQueue = new LinkedList<>();

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

		public KThread nextThread() {
			ThreadState next = pickNextThread(); // 최적 스레드 선택
			if (next != null) {
				waitQueue.remove(next); // 대기 큐에서 선택된 스레드 제거
				next.acquire(this); // 해당 스레드가 자원을 소유
				return next.thread; // 스케줄러가 실행할 실제 객체 반환
			}
			return null; // 큐가 비어있으면 null
		}


		/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
		ThreadState best = null;
		for (ThreadState ts : waitQueue) {
			if (best == null || ts.getEffectivePriority() > best.getEffectivePriority() || (ts.getEffectivePriority() == best.getEffectivePriority() && ts.waitTime < best.waitTime)) {
				// 스레드들 간 우선순위 결정
				best = ts;
			}
		}
		return best; // 선택된 스레드 반환
	}


		public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
		protected long waitTime;
		/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // implement me
	    return priority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
	    // implement me
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
		waitTime = Machine.timer().getTime();
		waitQueue.waitQueue.add(this);
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
    }
}
