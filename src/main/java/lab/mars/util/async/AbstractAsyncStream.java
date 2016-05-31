package lab.mars.util.async;

import lab.mars.util.async.action.ThenAction;
import lab.mars.util.async.action.ThenOnEventAction;

/**
 * Created by wumo on 2016/5/26.
 * email: wumo@outlook.com
 */
public interface AbstractAsyncStream {

    /**
     * @fmt:off
     * AsyncStream设计细节。
     * 每个action都有开始执行的条件和结束执行的条件。
     *      o开始执行的条件：由action的参数列表表示，无参数的action无开始执行条件（
     *          即按顺序轮到此action执行时便可立即执行），而有参数的action，则需要获取
     *          与参数数量相等的event才可执行（即此action必须等到event队列中至少有足
     *          够的event时才可执行）；
     *      o结束执行的条件：由action的返回值决定，无返回值的action无结束执行条件
     *          （即action结束后便可立即尝试执行下一个action），而具有返回值
     * @fmt:on
     */

    AbstractAsyncStream then(ThenAction thenAction);

    <EventType> AbstractAsyncStream then(ThenOnEventAction<EventType> thenOnEventAction);
}
