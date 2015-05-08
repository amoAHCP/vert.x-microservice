package org.jacpfx.common;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by amo on 27.10.14.
 * TODO remove setter
 */
public class ServiceInfo implements Serializable {
    private final String serviceName;
    private String lastConnection;
    private final String hostName;
    private final String serviceURL;
    private final String description;
    private final Operation[] operations;
    private transient Vertx vertx;

    public ServiceInfo(String serviceName, Operation... operations) {
        this(serviceName, null, null, null, null, operations);
    }

    public ServiceInfo(String serviceName, String lastConnection, String hostName, String serviceURL, String description, Operation... operations) {
        this(serviceName,lastConnection,hostName,serviceURL,description,null,operations);
    }

    public ServiceInfo(String serviceName, String lastConnection, String hostName, String serviceURL, String description,Vertx vertx, Operation... operations) {
        this.serviceName = serviceName;
        this.lastConnection = lastConnection;
        this.hostName = hostName;
        this.serviceURL = serviceURL;
        this.description = description;
        this.operations = operations;
        this.vertx = vertx;
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


    public Operation[] getOperations() {
        return operations;
    }


    public String getHostName() {
        return hostName;
    }


    public String getServiceURL() {
        return serviceURL;
    }


    public String getDescription() {
        return description;
    }


    public Stream<Operation> getOperations(final String name) {
        return Stream.of(operations).filter(op -> op.getName().equalsIgnoreCase(name));
    }

    public Optional<Operation> getOperation(final String name) {
        return Stream.of(operations).filter(op -> op.getName().equalsIgnoreCase(name)).findFirst();
    }

    public Stream<Operation> getOperationsByType(final Type type) {
        final String typeString = type.toString();
        return Stream.of(operations).filter(op -> op.getType().equalsIgnoreCase(typeString));
    }

    public static ServiceInfo buildFromJson(JsonObject info) {
        return buildFromJson(info,null);
    }

    public static ServiceInfo buildFromJson(JsonObject info,Vertx vertx) {
        final String serviceName = info.getString("serviceName");
        final String lastConnection = info.getString("lastConnection");
        final String hostName = info.getString("hostName");
        final String serviceURL = info.getString("serviceURL");
        final String description = info.getString("description");
        final List<Operation> operations  = JSONTool.getObjectListFromArray(info.getJsonArray("operations")).
                stream().
                map(operation -> addOperation(operation,vertx)).
                collect(Collectors.toList());

        return new ServiceInfo(serviceName, lastConnection, hostName, serviceURL, description, vertx,operations.toArray(new Operation[operations.size()]));
    }

    private static Operation addOperation(JsonObject operation,Vertx vertx) {
        final String type = operation.getString("type");
        final String url = operation.getString("url");
        final String name = operation.getString("name");
        final String serviceName1 = operation.getString("serviceName");
        final String connectionHost = operation.getString("connectionHost");
        final String connectionPort = operation.getInteger("connectionPort").toString();
        final String descriptionOperation = operation.getString("description");
        final JsonArray produces = operation.getJsonArray("produces");
        final List<String> producesTypes = JSONTool.getObjectListFromArray(produces).stream().map(m -> m.getString("produces")).collect(Collectors.toList());
        final JsonArray consumes = operation.getJsonArray("consumes");
        final List<String> consumesTypes = JSONTool.getObjectListFromArray(consumes).stream().map(m -> m.getString("consumes")).collect(Collectors.toList());
        final JsonArray params = operation.getJsonArray("param");
        final List<String> paramsList = JSONTool.getObjectListFromArray(params).stream().map(m -> m.getString("param")).collect(Collectors.toList());
        return new Operation(name,
                descriptionOperation,
                url,
                type,
                producesTypes.toArray(new String[producesTypes.size()]),
                consumesTypes.toArray(new String[consumesTypes.size()]),
                serviceName1,
                connectionHost,
                Integer.valueOf(connectionPort).intValue(),
                vertx,
                paramsList.toArray(new String[paramsList.size()]));
    }

    public  ServiceInfo buildFromServiceInfo(String serviceURL, Operation ...operations) {

        return new ServiceInfo(serviceName,lastConnection,hostName,serviceURL,description,operations);
    }


    public static JsonObject buildFromServiceInfo(ServiceInfo info) {
        final JsonObject tmp = new JsonObject();
        final JsonArray operationsArray = new JsonArray();
        Stream.of(info.getOperations()).forEach(op -> operationsArray.add(createOperation(op)));
        tmp.put("serviceName", info.getServiceName());
        tmp.put("lastConnection", info.getLastConnection());
        tmp.put("hostName", info.getHostName());
        tmp.put("serviceURL", info.getServiceURL());
        tmp.put("description", info.getDescription());
        tmp.put("operations", operationsArray);
        return tmp;
    }

    private static JsonObject createOperation(Operation op) {
        return JSONTool.createOperationObject(op);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceInfo)) return false;

        ServiceInfo that = (ServiceInfo) o;

        if (serviceName != null ? !serviceName.equals(that.serviceName) : that.serviceName != null) return false;
        if (lastConnection != null ? !lastConnection.equals(that.lastConnection) : that.lastConnection != null)
            return false;
        if (hostName != null ? !hostName.equals(that.hostName) : that.hostName != null) return false;
        if (serviceURL != null ? !serviceURL.equals(that.serviceURL) : that.serviceURL != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(operations, that.operations);

    }

    @Override
    public int hashCode() {
        int result = serviceName != null ? serviceName.hashCode() : 0;
        result = 31 * result + (lastConnection != null ? lastConnection.hashCode() : 0);
        result = 31 * result + (hostName != null ? hostName.hashCode() : 0);
        result = 31 * result + (serviceURL != null ? serviceURL.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (operations != null ? Arrays.hashCode(operations) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "serviceName='" + serviceName + '\'' +
                ", lastConnection='" + lastConnection + '\'' +
                ", hostName='" + hostName + '\'' +
                ", serviceURL='" + serviceURL + '\'' +
                ", description='" + description + '\'' +
                ", operations=" + Arrays.toString(operations) +
                '}';
    }
}
