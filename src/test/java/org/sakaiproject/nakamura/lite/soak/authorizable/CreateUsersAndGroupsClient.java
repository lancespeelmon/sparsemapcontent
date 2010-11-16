package org.sakaiproject.nakamura.lite.soak.authorizable;

import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.lite.accesscontrol.AccessControlManagerImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.AuthenticatorImpl;
import org.sakaiproject.nakamura.lite.authorizable.AuthorizableManagerImpl;
import org.sakaiproject.nakamura.lite.soak.AbstractScalingClient;
import org.sakaiproject.nakamura.lite.storage.ConnectionPool;
import org.sakaiproject.nakamura.lite.storage.ConnectionPoolException;
import org.sakaiproject.nakamura.lite.storage.StorageClientException;

import com.google.common.collect.ImmutableMap;

public class CreateUsersAndGroupsClient extends AbstractScalingClient {

    private int nusers;

    public CreateUsersAndGroupsClient(int totalUsers, ConnectionPool connectionPool) throws ConnectionPoolException,
            StorageClientException, AccessDeniedException {
        super(connectionPool);
        nusers = totalUsers;
    }

    @Override
    public void run() {
        try {
            super.setup();
            String tname = String.valueOf(Thread.currentThread().getId())
                    + String.valueOf(System.currentTimeMillis());
            AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
            User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

            AccessControlManagerImpl accessControlManagerImpl = new AccessControlManagerImpl(
                    client, currentUser, configuration);

            AuthorizableManagerImpl authorizableManager = new AuthorizableManagerImpl(currentUser,
                    client, configuration, accessControlManagerImpl);

            for (int i = 0; i < nusers; i++) {
                String userId = tname + "_" + i;
                authorizableManager.createUser(userId, userId, "test", ImmutableMap.of(userId,
                        (Object) "testvalue", "principals", "administrators;testers",
                        Authorizable.GROUP_FIELD, Authorizable.GROUP_VALUE));
            }

        } catch (StorageClientException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (AccessDeniedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ConnectionPoolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
