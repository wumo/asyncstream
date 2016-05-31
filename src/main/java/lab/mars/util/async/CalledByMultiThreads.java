package lab.mars.util.async;

import java.lang.annotation.*;

/**
 * <p>
 * Created by wumo on 2016/5/31.<br>
 * email: wumo@outlook.com<br>
 * </p>
 * 表示此方法可能同时由多个线程调用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface CalledByMultiThreads {
}
