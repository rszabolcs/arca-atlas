package com.arcadigitalis.backend.lit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class AccTemplateBuilder {

    private final ChainNameRegistry chainNameRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccTemplateBuilder(ChainNameRegistry chainNameRegistry) {
        this.chainNameRegistry = chainNameRegistry;
    }

    public ObjectNode buildAccTemplate(Long chainId, String proxyAddress, String packageKey, String beneficiaryAddress) {
        String litChainName = chainNameRegistry.getChainName(chainId);

        ObjectNode acc = objectMapper.createObjectNode();
        acc.put("conditionType", "evmContract");
        acc.put("contractAddress", proxyAddress);
        acc.put("functionName", "isReleased");

        ArrayNode functionParams = acc.putArray("functionParams");
        functionParams.add(packageKey);

        ObjectNode functionAbi = acc.putObject("functionAbi");
        functionAbi.put("name", "isReleased");
        functionAbi.put("stateMutability", "view");
        functionAbi.put("type", "function");

        ArrayNode inputs = functionAbi.putArray("inputs");
        ObjectNode input = inputs.addObject();
        input.put("name", "packageKey");
        input.put("type", "bytes32");

        ArrayNode outputs = functionAbi.putArray("outputs");
        ObjectNode output = outputs.addObject();
        output.put("name", "");
        output.put("type", "bool");

        acc.put("chain", litChainName);

        ObjectNode returnValueTest = acc.putObject("returnValueTest");
        returnValueTest.put("key", "");
        returnValueTest.put("comparator", "=");
        returnValueTest.put("value", "true");

        // Requester constraint
        acc.put("requester", beneficiaryAddress);

        return acc;
    }
}
