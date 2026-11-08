package by.moby.family.requests;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;

/** Предмет запроса исчез: запрос переводится в CANCELLED, и это изменение сохраняется (noRollbackFor). */
public class RequestCancelledException extends BusinessException {

    public RequestCancelledException(String reason) {
        super(ErrorCode.NOT_FOUND, reason);
    }
}
