package com.arcadigitalis.backend.evm;

import com.arcadigitalis.backend.api.exception.RpcUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

@Service
public class PolicyReader {

    private final Web3j web3j;

    @Value("${arca.evm.policy-proxy-address}")
    private String proxyAddress;

    public PolicyReader(Web3j web3j) {
        this.web3j = web3j;
    }

    public PackageView getPackage(String packageKey) {
        try {
            Function function = new Function(
                "getPackage",
                Arrays.asList(new org.web3j.abi.datatypes.generated.Bytes32(hexToBytes32(packageKey))),
                Arrays.asList(
                    new TypeReference<Uint8>() {},           // status
                    new TypeReference<Address>() {},         // owner
                    new TypeReference<Address>() {},         // beneficiary
                    new TypeReference<Utf8String>() {},      // manifestUri
                    new TypeReference<DynamicArray<Address>>() {}, // guardians
                    new TypeReference<Uint256>() {},         // guardianQuorum
                    new TypeReference<Uint256>() {},         // vetoCount
                    new TypeReference<Uint256>() {},         // approvalCount
                    new TypeReference<Uint256>() {},         // warnThreshold
                    new TypeReference<Uint256>() {},         // inactivityThreshold
                    new TypeReference<Uint256>() {},         // gracePeriodSeconds
                    new TypeReference<Uint256>() {},         // lastCheckIn
                    new TypeReference<Uint256>() {},         // paidUntil
                    new TypeReference<Uint256>() {},         // pendingSince
                    new TypeReference<Uint256>() {}          // releasedAt
                )
            );

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, proxyAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            if (response.hasError()) {
                throw new RpcUnavailableException("RPC call failed: " + response.getError().getMessage());
            }

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

            return new PackageView(
                mapStatus(((Uint8) results.get(0)).getValue().intValue()),
                ((Address) results.get(1)).getValue(),
                ((Address) results.get(2)).getValue(),
                ((Utf8String) results.get(3)).getValue(),
                ((DynamicArray<Address>) results.get(4)).getValue().stream().map(Address::getValue).toList(),
                ((Uint256) results.get(5)).getValue(),
                ((Uint256) results.get(6)).getValue(),
                ((Uint256) results.get(7)).getValue(),
                ((Uint256) results.get(8)).getValue(),
                ((Uint256) results.get(9)).getValue(),
                ((Uint256) results.get(10)).getValue(),
                ((Uint256) results.get(11)).getValue(),
                ((Uint256) results.get(12)).getValue(),
                ((Uint256) results.get(13)).getValue(),
                ((Uint256) results.get(14)).getValue()
            );

        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to read package from chain", e);
        }
    }

    public String getPackageStatus(String packageKey) {
        try {
            Function function = new Function(
                "getPackageStatus",
                Arrays.asList(new org.web3j.abi.datatypes.generated.Bytes32(hexToBytes32(packageKey))),
                Arrays.asList(new TypeReference<Uint8>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, proxyAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            if (response.hasError()) {
                throw new RpcUnavailableException("RPC call failed: " + response.getError().getMessage());
            }

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            int statusValue = ((Uint8) results.get(0)).getValue().intValue();

            return mapStatus(statusValue);

        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to read package status from chain", e);
        }
    }

    public boolean isReleased(String packageKey) {
        try {
            Function function = new Function(
                "isReleased",
                Arrays.asList(new org.web3j.abi.datatypes.generated.Bytes32(hexToBytes32(packageKey))),
                Arrays.asList(new TypeReference<Bool>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, proxyAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            if (response.hasError()) {
                throw new RpcUnavailableException("RPC call failed: " + response.getError().getMessage());
            }

            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            return ((Bool) results.get(0)).getValue();

        } catch (Exception e) {
            throw new RpcUnavailableException("Failed to check if package is released", e);
        }
    }

    private byte[] hexToBytes32(String hex) {
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        byte[] bytes = new byte[32];
        byte[] hexBytes = org.web3j.utils.Numeric.hexStringToByteArray(clean);
        System.arraycopy(hexBytes, 0, bytes, 0, Math.min(hexBytes.length, 32));
        return bytes;
    }

    private String mapStatus(int statusValue) {
        return switch (statusValue) {
            case 0 -> "DRAFT";
            case 1 -> "ACTIVE";
            case 2 -> "WARNING";
            case 3 -> "PENDING_RELEASE";
            case 4 -> "CLAIMABLE";
            case 5 -> "RELEASED";
            case 6 -> "REVOKED";
            default -> "UNKNOWN";
        };
    }

    public record PackageView(
        String status,
        String ownerAddress,
        String beneficiaryAddress,
        String manifestUri,
        List<String> guardians,
        BigInteger guardianQuorum,
        BigInteger vetoCount,
        BigInteger approvalCount,
        BigInteger warnThreshold,
        BigInteger inactivityThreshold,
        BigInteger gracePeriodSeconds,
        BigInteger lastCheckIn,
        BigInteger paidUntil,
        BigInteger pendingSince,
        BigInteger releasedAt
    ) {}
}
