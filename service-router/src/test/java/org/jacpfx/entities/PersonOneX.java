package org.jacpfx.entities;

import java.io.Serializable;

/**
 * Created by Andy Moncsek on 28.04.15.
 */
public class PersonOneX implements Serializable {
    private final String name;
    private final String lastname;


    public PersonOneX(String name, String lastname) {
        this.name = name;
        this.lastname = lastname;
    }

    public String getName() {
        return name;
    }

    public String getLastname() {
        return lastname;
    }

    @Override
    public String toString() {
        return "PersonOneX{" +
                "name='" + name + '\'' +
                ", lastname='" + lastname + '\'' +
                '}';
    }
}

