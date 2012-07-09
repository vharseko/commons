/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.json.resource;

import java.util.List;

import org.forgerock.json.fluent.JsonPointer;

/**
 * A request to delete a JSON resource.
 */
public interface DeleteRequest extends Request {

    /**
     * {@inheritDoc}
     */
    @Override
    DeleteRequest addFieldFilter(JsonPointer... fields);

    /**
     * {@inheritDoc}
     */
    @Override
    DeleteRequest addFieldFilter(String... fields);

    /**
     * {@inheritDoc}
     */
    @Override
    String getComponent();

    /**
     * {@inheritDoc}
     */
    @Override
    List<JsonPointer> getFieldFilters();

    /**
     * {@inheritDoc}
     */
    @Override
    String getResourceId();

    /**
     * Returns the expected version information associated with the JSON
     * resource to be deleted. Version information can be used in order to
     * implement multi-version concurrency control (MVCC).
     * <p>
     * The returned version information may be {@code null} indicating that the
     * client does not care if the resource has been modified concurrently. If
     * the version information is non-{@code null}, and it does not match the
     * current version of the targeted JSON resource, then the delete request
     * will be rejected by the provider.
     *
     * @return The expected version information associated with the JSON
     *         resource to be deleted.
     */
    String getRevision();

    /**
     * {@inheritDoc}
     */
    @Override
    DeleteRequest setComponent(String path);

    /**
     * {@inheritDoc}
     */
    @Override
    DeleteRequest setResourceId(String id);

    /**
     * Sets the expected version information associated with the JSON resource
     * to be deleted. Version information can be used in order to implement
     * multi-version concurrency control (MVCC).
     * <p>
     * The provided version information may be {@code null} indicating that the
     * client does not care if the resource has been modified concurrently. If
     * the version information is non-{@code null}, and it does not match the
     * current version of the targeted JSON resource, then the delete request
     * will be rejected by the provider.
     *
     * @param version
     *            The expected version information associated with the JSON
     *            resource to be deleted.
     * @return This delete request.
     * @throws UnsupportedOperationException
     *             If this delete request does not permit changes to the version
     *             information.
     */
    DeleteRequest setRevision(String version);

}
