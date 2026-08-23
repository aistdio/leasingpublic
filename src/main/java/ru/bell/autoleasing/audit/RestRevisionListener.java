package ru.bell.autoleasing.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.envers.RevisionListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.utilities.ClientIp;
import ru.bell.autoleasing.utilities.validation.DataValidator;

public class RestRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        RestRevisionEntity restRevisionEntity = (RestRevisionEntity) revisionEntity;

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String clientIp = ClientIp.getClientIP(request);
            try {
                clientIp = DataValidator.validateAndNormalizeIpAddress(clientIp);
                restRevisionEntity.setClientIp(clientIp);
            } catch (ValidationException e) {
                restRevisionEntity.setClientIp("unknown");
            }
        } else {
            restRevisionEntity.setClientIp("unknown");
        }
    }
}
