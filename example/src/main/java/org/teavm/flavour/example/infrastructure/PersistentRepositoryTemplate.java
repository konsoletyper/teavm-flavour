/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.example.infrastructure;

import javax.persistence.EntityManager;
import org.jinq.jpa.JinqJPAStreamProvider;
import org.jinq.orm.stream.JinqStream;
import org.teavm.flavour.example.model.GenericRepository;

/**
 *
 * @author Alexey Andreev
 */
public abstract class PersistentRepositoryTemplate<T> implements GenericRepository<T> {
    private EntityManager em;
    private Class<T> type;
    private JinqJPAStreamProvider streamProvider;

    protected PersistentRepositoryTemplate(EntityManager em, Class<T> type) {
        this.em = em;
        this.type = type;
        streamProvider = new JinqJPAStreamProvider(em.getEntityManagerFactory());
    }

    @Override
    public void add(T entity) {
        em.persist(entity);
    }

    @Override
    public void remove(T entity) {
        em.remove(entity);
    }

    @Override
    public boolean contains(T entity) {
        return em.contains(entity);
    }

    @Override
    public Integer getId(T entity) {
        return (Integer) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    @Override
    public T findById(int id) {
        return em.find(type, em);
    }

    @Override
    public JinqStream<T> all() {
        return streamProvider.streamAll(em, type);
    }
}
