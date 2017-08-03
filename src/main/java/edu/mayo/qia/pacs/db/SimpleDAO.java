package edu.mayo.qia.pacs.db;

import com.google.common.base.Optional;

import org.hibernate.SessionFactory;

import java.util.List;

import io.dropwizard.hibernate.AbstractDAO;

public class SimpleDAO<T> extends AbstractDAO<T> {

  public SimpleDAO(SessionFactory sessionFactory) {
    super(sessionFactory);
  }

  public Optional<T> findById(Long id) {
    return Optional.fromNullable(get(id));
  }

  public T get(int i) {
    return get(new Integer(i));
  }

  public T create(T t) {
    return persist(t);
  }

  @SuppressWarnings("unchecked")
  public List<T> findAll(Class<T> c) {
    return currentSession().createCriteria(c).list();
  }

  public List<T> findAll() {
    return findAll(this.getEntityClass());
  }

  public void delete(T t) {
    currentSession().delete(t);
  }

  public T update(T t) {
    currentSession().saveOrUpdate(t);
    return t;
  }

  public void commit() {
    if (currentSession().getTransaction() != null && currentSession().getTransaction().isActive()) {
      currentSession().getTransaction().commit();
    }
  }
}
