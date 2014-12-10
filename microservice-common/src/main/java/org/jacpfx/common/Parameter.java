package org.jacpfx.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by amo on 27.10.14.
 */
public class Parameter<T> implements Serializable{
    private  List<Parameter<T>> all = new ArrayList<>();
    private String name;
    private T value;

    public Parameter(String name, T value) {
        this.name = name;
        this.value = value;

    }

    public Parameter(List<Parameter<T>> all) {
       this.all.addAll(all);

    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public T getValue(String name) {
        if(this.name!=null && this.name.equals(name)) return this.value;
        final Optional<Parameter<T>> first = all.stream().filter(p -> p.getName().equals(name)).findFirst();
        return first.isPresent()?first.get().getValue():null;
    }

    public List<Parameter<T>> getAll() {
        return all;
    }
}
