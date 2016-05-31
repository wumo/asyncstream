package lab.mars.util.async;

import java.lang.annotation.*;

/**
 * <p>
 * Created by wumo on 2016/5/31.<br>
 * email: wumo@outlook.com<br>
 * </p>
 * 此方法仅会由单线程调用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface CalledBySingleThread {
}
