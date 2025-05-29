package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */

    private class BlockThread {
        KThread thread;
        long time;

        BlockThread(KThread thread, long time){
            this.thread = thread;
            this.time = time;
        }
    }

    private ArrayList<BlockThread> threadList = new ArrayList<>();

    public void timerInterrupt() {
	    // KThread.currentThread().yield();

        // 현재 시간 가져오기
        long preTime = Machine.timer().getTime();
        boolean interrupted = Machine.interrupt().disable();

        Iterator<BlockThread> iterator = threadList.iterator();
        while(iterator.hasNext()){
            BlockThread blockThread = iterator.next();
            // 현재 시간과 블록된 쓰레드의 블록된 시간 비교
            if(blockThread.time <= preTime){
                iterator.remove(); // 지우고

                // 블록된 쓰레드 깨우기
                blockThread.thread.ready();
            }
        }

        Machine.interrupt().restore(interrupted); // 인터럽트 복원

        // 현재 쓰레드 양보
        KThread.currentThread().yield();

    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */

    /*
    * 구현 가이드
    - waitUntil() 메소드 및 timer 인터럽트 핸들러만 수정하면 됨
    - block된 쓰레드들의 리스트를 만들고 timer 인터럽트 발생할 때마다 timer tick이 x만큼
        지났는지 검사 후 만약 지났을 경우 해당 쓰레드를 wake-up 시킴
    - x Timer tick이 0 또는 음수인 경우 기다리지 않고 즉시 리턴함
    * */

    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad) // busy waiting 방식으로 구현되어있음
//        long wakeTime = Machine.timer().getTime() + x;
//        while (wakeTime > Machine.timer().getTime())
//            KThread.yield();

        // x가 0 이하면 바로
        if(x <= 0) return;


        // 현재 시간이랑 깨어날 시간 계산
        long preTime = Machine.timer().getTime();
        boolean interrupted = Machine.interrupt().disable();

        // 블록된 쓰레드 리스트에 추가
        threadList.add(new BlockThread(KThread.currentThread(), preTime + x));

        // 현재 쓰레드 블록
        KThread.sleep();

        Machine.interrupt().restore(interrupted); // 인터럽트 복원
    }

    // 4번 문제
    public static void alarmTest1() {
        int durations[] = {1000, 10*1000, 100*1000};
        long t0, t1;

        for (int d : durations) {
            t0 = Machine.timer().getTime();
            ThreadedKernel.alarm.waitUntil (d);
            t1 = Machine.timer().getTime();
            System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
        }
    }

    // Implement more test methods here ...
    public static void alarmTest2() {
        int[] durations = {1000, 2000, 3000}; // 대기 시간

        int num = 0; // Thread id
        for (int d : durations) {
            int id = num;

            new KThread(new Runnable() {
                public void run() {
                    // 현재시각 가져와서 t0 저장 => waitUtil 호출 전
                    long t0 = Machine.timer().getTime();
                    System.out.println("Thread " + id + ": waitUntil() 호출 전 시간 = " + t0 + ", 대기 시간 = " +d + " ticks");

                    ThreadedKernel.alarm.waitUntil(d); // Alarm::waitUntil() 호출, 현재 쓰레드 최소 d ticks 동안 block
                    // wake-up 후 현재 시간을 측정
                    long t1 = Machine.timer().getTime();
                    System.out.println("Thread " + id + ": 쓰레드가 wake-up된 후 시간 = " + t1 + ", 실제 대기 시간 = " + (t1 - t0) + " ticks");
                }
            }).setName("AlarmTest2-Thread-" + num).fork();
            num++;
        }

        // 자식 쓰레드가 모두 끝나기 전에 메인 쓰레드가 종료되면 안되니 시간 대기.
        ThreadedKernel.alarm.waitUntil(4000);
    }

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
        alarmTest1();
        System.out.println("--------------alarmTest2-------------");
        alarmTest2();

        // Invoke your other test methods here ...
    }
}
