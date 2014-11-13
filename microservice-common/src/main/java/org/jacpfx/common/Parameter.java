package org.jacpfx.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amo on 27.10.14.
 */
public class Parameter<T> {
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
        return all.stream().filter(p->p.getName().equals(name)).findFirst().get().getValue();
    }

    public List<Parameter<T>> getAll() {
        return all;
    }
}
