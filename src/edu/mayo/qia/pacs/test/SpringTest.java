package edu.mayo.qia.pacs.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import edu.mayo.qia.pacs.PACS;

@RunWith(SpringJUnit4ClassRunner.class)
public class SpringTest extends PACSTest {

  @Test
  public void configuration() {
    assertTrue(PACS.context != null);
  }

}
