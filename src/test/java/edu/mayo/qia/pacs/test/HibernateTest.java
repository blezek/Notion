package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertTrue;

import org.apache.log4j.Level;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;

@RunWith(SpringJUnit4ClassRunner.class)
public class HibernateTest extends PACSTest {

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Before
  public void setup() {
    template.update("delete from Device");
    template.update("delete from Pool");
  }

  @Test
  public void insertPools() {

    // Try to save an object
    Session session;
    session = sessionFactory.openSession();
    session.beginTransaction();
    session.save(new Pool("One", "One", "one", false));
    session.getTransaction().commit();
    session.close();

    // Verify it!
    assertTrue("Inserted", 1 == template.queryForObject("select count(*) from Pool", Integer.class).intValue());

    // Try to find it
    session = sessionFactory.openSession();
    Criteria criteria = session.createCriteria(Pool.class);
    criteria.add(Restrictions.eq("name", "One"));

    @SuppressWarnings("unchecked")
    List<Pool> result = criteria.list();
    assertTrue("Fetched pool", result.size() == 1);
    assertTrue("Has the proper comment", result.get(0).description.equals("One"));

    result.get(0).name = "Two";
    session.beginTransaction();
    session.update(result.get(0));
    session.getTransaction().commit();
    assertTrue("Updated", 1 == template.queryForObject("select count(*) from Pool where Name='Two'", Integer.class).intValue());

    // Delete it
    session.beginTransaction();
    session.delete(result.get(0));
    session.getTransaction().commit();
    session.close();
    assertTrue("Deleted", 0 == template.queryForObject("select count(*) from Pool", Integer.class).intValue());
  }

  @Test
  public void createDevice() {
    Pool pool;
    // Try to save an object
    Session session;
    session = sessionFactory.openSession();
    session.beginTransaction();
    pool = new Pool("One", "One", "bar", false);
    session.save(pool);
    session.getTransaction().commit();
    int poolKey = pool.poolKey;

    session.beginTransaction();
    pool = (Pool) session.byId(Pool.class).getReference(poolKey);
    Device device = new Device();
    device.applicationEntityTitle = "AE";
    device.hostName = "hostie";
    device.port = 12345;

    /* Here we need to set the pool, so the device knows about it. */
    device.setPool(pool);

    // Likewise, tell the pool about the new device
    pool.getDevices().add(device);

    // The pool will automatically save all the devices inside it.
    session.saveOrUpdate(pool);

    session.getTransaction().commit();
    assertTrue("Assigned device key", device.deviceKey != 0);
    session.close();
    logger.setLevel(Level.DEBUG);
    template.query("select * from Pool", DumpHandler.getDumper(logger));
    template.query("select * from Device", DumpHandler.getDumper(logger));
    assertTrue("PoolKey is set", template.queryForObject("select PoolKey from Device where DeviceKey = ?", new Object[] { device.deviceKey }, Integer.class) != 0);
  }
}
