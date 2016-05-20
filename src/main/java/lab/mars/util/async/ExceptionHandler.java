package lab.mars.util.async;

/**
 * Created by haixiao on 2015/4/22.
 * Email: wumo@outlook.com
 */
@FunctionalInterface
public interface ExceptionHandler {
    void handle(Throwable e);
}
