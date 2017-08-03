package edu.mayo.qia.pacs.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.sun.jersey.api.NotFoundException;

import org.apache.log4j.Logger;

@Provider
public class ExceptionHandler implements ExceptionMapper<NotFoundException> {
  static Logger logger = Logger.getLogger(ExceptionHandler.class);

  @Override
  public Response toResponse(NotFoundException exception) {
    logger.error("Exception", exception);
    return Response.status(Response.Status.NOT_FOUND).build();
  }
}
