package com.arcadigitalis.backend.lit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Builds Lit Access Control Condition (ACC) JSON templates.
 * <p>
 * The ACC binds vault-key unwrapping to:
 * <ul>
 *   <li>{@code policyProxy.isReleased(packageKey) == true} on the specified proxy</li>
 *   <li>requester constrained to the beneficiary address</li>
 * </ul>
 * <p>
 * No Lit network calls — this is pure JSON template construction (research §3).
 *
 * @see <a href="https://developer.litprotocol.com/sdk/access-control/evm/custom-contract-calls">Lit ACC docs</a>
 */
@Component
public class AccTemplateBuilder {

    private final ChainNameRegistry chainNameRegistry;
    private final ObjectMapper objectMapper;

    public AccTemplateBuilder(ChainNameRegistry chainNameRegistry) {
        this.chainNameRegistry = chainNameRegistry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Builds a complete ACC JSON node for the given parameters.
     * <p>
     * Generated structure matches the canonical template in research.md §3.
     *
     * @param chainId              EVM chain ID
     * @param proxyAddress         Policy proxy address (EIP-55)
     * @param packageKey           bytes32 package key (0x-prefixed)
     * @param beneficiaryAddress   Beneficiary address (EIP-55) — used as requester constraint
     * @return complete ACC JSON ObjectNode
     */
    public ObjectNode buildAccTemplate(long chainId, String proxyAddress,
                                        String packageKey, String beneficiaryAddress) {
        String chain = chainNameRegistry.getChainName(chainId);

        ObjectNode acc = objectMapper.createObjectNode();
        acc.put("conditionType", "evmContract");
        acc.put("contractAddress", proxyAddress);
        acc.put("functionName", "isReleased");

        // functionParams: [packageKey]
        ArrayNode functionParams = objectMapper.createArrayNode();
        functionParams.add(packageKey);
        acc.set("functionParams", functionParams);

        // functionAbi
        ObjectNode functionAbi = buildFunctionAbi();
        acc.set("functionAbi", functionAbi);

        acc.put("chain", chain);

        // returnValueTest: { key: "", comparator: "=", value: "true" }
        ObjectNode returnValueTest = objectMapper.createObjectNode();
        returnValueTest.put("key", "");
        returnValueTest.put("comparator", "=");
        returnValueTest.put("value", "true");
        acc.set("returnValueTest", returnValueTest);

        // requester constraint
        acc.put("requester", beneficiaryAddress);

        return acc;
    }

    private ObjectNode buildFunctionAbi() {
        ObjectNode abi = objectMapper.createObjectNode();
        abi.put("name", "isReleased");
        abi.put("type", "function");
        abi.put("stateMutability", "view");

        // inputs: [{ name: "packageKey", type: "bytes32" }]
        ArrayNode inputs = objectMapper.createArrayNode();
        ObjectNode input = objectMapper.createObjectNode();
        input.put("name", "packageKey");
        input.put("type", "bytes32");
        inputs.add(input);
        abi.set("inputs", inputs);

        // outputs: [{ name: "", type: "bool" }]
        ArrayNode outputs = objectMapper.createArrayNode();
        ObjectNode output = objectMapper.createObjectNode();
        output.put("name", "");
        output.put("type", "bool");
        outputs.add(output);
        abi.set("outputs", outputs);

        return abi;
    }
}
