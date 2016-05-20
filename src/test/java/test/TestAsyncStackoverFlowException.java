package test;

import lab.mars.util.async.AsyncStream;
import org.junit.Test;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static lab.mars.util.async.AsyncStream.instantAsync;

/**
 * Created by haixiao on 8/10/2015.
 * Email: wumo@outlook.com
 */
public class TestAsyncStackoverFlowException {

    @Test
    public void test() {
        List<Object> list = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            list.add(i);
        }
        nextPhase(list.iterator());
    }

    void nextPhase(Iterator iterator) {
        if (iterator.hasNext())
            recoverThisOne(iterator.next()).then(() -> nextPhase(iterator));
        //在执行instantAction之前应该
        else
            finish();
    }

    AsyncStream recoverThisOne(Object object) {
        System.out.println(object);
        return instantAsync();
    }

    void finish() {
        System.out.println("finish");
    }
}
