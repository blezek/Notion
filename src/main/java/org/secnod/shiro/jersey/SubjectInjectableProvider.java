package org.secnod.shiro.jersey;

import com.sun.jersey.spi.inject.Injectable;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

public class SubjectInjectableProvider extends AuthInjectableProvider<Subject> {

    public SubjectInjectableProvider() {
        super(Subject.class);
    }

    private final Injectable<Subject> subjectInjectable = new Injectable<Subject>() {
        @Override
        public Subject getValue() {
            return SecurityUtils.getSubject();
        }
    };

    @Override
    public Injectable<Subject> getInjectable() {
        return subjectInjectable;
    }
}
