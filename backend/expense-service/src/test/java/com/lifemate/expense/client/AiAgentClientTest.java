package com.lifemate.expense.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(OutputCaptureExtension.class)
class AiAgentClientTest {

    @Test
    void parseLogsDoNotContainRawTextOrParsedTransactionDetails(CapturedOutput output) throws Exception {
        String rawText = "PRIVATE_SMS_BODY_938475";
        String merchant = "PRIVATE_MERCHANT";
        String amount = "123.45";
        byte[] responseBody = """
                {
                  "transaction_type": "EXPENSE",
                  "amount": 123.45,
                  "currency": "INR",
                  "category": "Other",
                  "merchant": "PRIVATE_MERCHANT",
                  "confidence_score": 0.95
                }
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ai/parse-expense", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });

        try {
            server.start();
            AiAgentClient client = new AiAgentClient("http://127.0.0.1:" + server.getAddress().getPort());

            var parsed = client.parseExpense(rawText, "INR");

            assertEquals(merchant, parsed.getMerchant());
            assertFalse(output.getOut().contains(rawText));
            assertFalse(output.getOut().contains(merchant));
            assertFalse(output.getOut().contains(amount));
        } finally {
            server.stop(0);
        }
    }
}
