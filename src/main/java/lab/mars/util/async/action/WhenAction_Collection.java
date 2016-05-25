package lab.mars.util.async.action;

import lab.mars.util.async.AsyncStream;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created by haixiao on 8/6/2015.
 * Email: wumo@outlook.com
 */
public class WhenAction_Collection implements WhenAction {
    Collection<AsyncStream> asyncs;

    public WhenAction_Collection(Collection<AsyncStream> asyncs) {
        this.asyncs = asyncs;
    }

    public void run(Consumer awakeFunc){
        AtomicInteger count=new AtomicInteger(0);
        int size=asyncs.size();
        for (AsyncStream async : asyncs) {
            async.then(()->{
                int last=count.incrementAndGet();
                if(last==size){//the last one to execute
                    awakeFunc.accept(null);
                }
            }).end();
        }
    }
}
