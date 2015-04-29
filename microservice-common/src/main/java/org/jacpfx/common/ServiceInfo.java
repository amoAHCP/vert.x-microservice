package org.jacpfx.common;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by amo on 27.10.14.
 */
public class ServiceInfo implements Serializable{
    private String serviceName;
    private String lastConnection;
    private Operation[] operations;

    public ServiceInfo(String serviceName, Operation ...operations) {
        this.serviceName = serviceName;
        this.operations = operations;
    }

    public String getLastConnection() {
        return lastConnection;
    }

    public void setLastConnection(String lastConnection) {
        this.lastConnection = lastConnection;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Operation[] getOperations() {
        return operations;
    }

    public void setOperations(Operation[] operations) {
        this.operations = operations;
    }

    public static ServiceInfo buildFromJson(JsonObject info) {
        final String serviceName = info.getString("serviceName");
        final List<Operation> operations = new ArrayList<>();
        JSONTool.getObjectListFromArray(info.getJsonArray("operations")).forEach(operation->{
            final String type = operation.getString("type");
            final String url = operation.getString("url");
            final JsonArray mimes = operation.getJsonArray("mime");
            final List<String> mimeTypes = JSONTool.getObjectListFromArray(mimes).stream().map(m->m.getString("mime")).collect(Collectors.toList());
            final JsonArray params = operation.getJsonArray("param");
            final List<String> paramsList = JSONTool.getObjectListFromArray(params).stream().map(m->m.getString("param")).collect(Collectors.toList());
            operations.add(new Operation(url,type,mimeTypes.toArray(new String[mimeTypes.size()]),paramsList.toArray(new String[paramsList.size()])));
        });

        return new ServiceInfo(serviceName,operations.toArray(new Operation[operations.size()]));
    }


    public static JsonObject buildFromServiceInfo(ServiceInfo info) {
        final JsonObject tmp = new JsonObject();
        final JsonArray operationsArray = new JsonArray();
        Stream.of(info.getOperations()).forEach(op -> operationsArray.add(createOperation(op)));
        tmp.put("serviceName", info.getServiceName());
        tmp.put("lastConnection",info.getLastConnection());
        tmp.put("operations", operationsArray);
        return tmp;
    }

    private static JsonObject createOperation(Operation op) {
        return JSONTool.createOperationObject(op.getUrl(), op.getType(), op.getMime(), op.getConsumes(),op.getParameter());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceInfo)) return false;

        ServiceInfo that = (ServiceInfo) o;

        if (!Arrays.equals(operations, that.operations)) return false;
        if (!serviceName.equals(that.serviceName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = serviceName.hashCode();
        result = 31 * result + (operations != null ? Arrays.hashCode(operations) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceName='" + serviceName + '\'' +
                ", lastConnection='" + lastConnection + '\'' +
                ", operations=" + Arrays.toString(operations) +
                '}';
    }
}
