/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.domain.controller.DomainControllerLogger;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

import java.security.PrivilegedAction;
import java.util.List;

import javax.security.auth.Subject;

/**
 * Task responsible for updating a single server-group.
 *
 * @author Emanuel Muckenhuber
 */
// TODO cleanup ServerGroupRolloutTask vs. ServerUpdateTask vs. Concurrent/RollingUpdateTask
abstract class AbstractServerGroupRolloutTask implements Runnable {

    protected final List<ServerUpdateTask> tasks;
    protected final ServerUpdatePolicy updatePolicy;
    protected final ServerTaskExecutor executor;
    protected final ServerUpdateTask.ServerUpdateResultHandler resultHandler;
    protected final Subject subject;

    public AbstractServerGroupRolloutTask(List<ServerUpdateTask> tasks, ServerUpdatePolicy updatePolicy, ServerTaskExecutor executor, final ServerUpdateTask.ServerUpdateResultHandler resultHandler, Subject subject) {
        this.tasks = tasks;
        this.updatePolicy = updatePolicy;
        this.executor = executor;
        this.resultHandler = resultHandler;
        this.subject = subject;
    }

    @Override
    public void run() {
        try {
            AccessAuditContext.doAs(subject, new PrivilegedAction<Void>() {

                @Override
                public Void run() {
                    execute();
                    return null;
                }

            });
        } catch (Throwable t) {
            DomainControllerLogger.ROOT_LOGGER.debugf(t, "failed to process task %s", tasks.iterator().next().getOperation());
        }
    }

    /**
     * Execute the the rollout task.
     */
    protected abstract void execute();

    /**
     * Record a prepared operation.
     *
     * @param identity the server identity
     * @param prepared the prepared operation
     */
    protected void recordPreparedOperation(final ServerIdentity identity, final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> prepared) {
        final ModelNode preparedResult = prepared.getPreparedResult();
        // Hmm do the server results need to get translated as well as the host one?
        // final ModelNode transformedResult = prepared.getOperation().transformResult(preparedResult);
        updatePolicy.recordServerResult(identity, preparedResult);
        executor.recordPreparedOperation(prepared);
        resultHandler.handleServerUpdateResult(identity, preparedResult);
    }

    protected void sendCancelledResponse(ServerIdentity serverId) {
        final ModelNode response = new ModelNode();
        response.get(OUTCOME).set(CANCELLED);
        resultHandler.handleServerUpdateResult(serverId, response);
    }

}
