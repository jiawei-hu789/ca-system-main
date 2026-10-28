package io.github.fkwhao.casystem.common.service;


public interface AuditService {


    void record(long actorId, String operation, String targetType, String targetId);

}
