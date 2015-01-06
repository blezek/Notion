package edu.mayo.qia.pacs.metric;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.SlidingTimeWindowReservoir;

public class RateGauge implements Gauge<Double> {

  SlidingTimeWindowReservoir reservoir = new SlidingTimeWindowReservoir(1, TimeUnit.SECONDS);

  public void mark() {
    reservoir.update(1);
  }

  @Override
  public Double getValue() {
    return new Double(reservoir.size());
  }
}
