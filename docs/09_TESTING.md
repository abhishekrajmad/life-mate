# 09. TESTING STRATEGY & TEST SUITES

## 1. Testing Matrix
* **Unit Testing:** JUnit 5 + Mockito (Java services); pytest (Python AI service).
* **Integration Testing:** `@SpringBootTest` with Testcontainers / H2 for DB layer.
* **API Testing:** Exported Postman Collection executing end-to-end endpoint validations.

## 2. Sample JUnit 5 Unit Test (Expense Service)

```java
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private AiAgentClient aiAgentClient;

    @InjectMocks
    private ExpenseServiceImpl expenseService;

    @Test
    void whenValidInput_thenParseAndSaveTransaction() {
        // Given
        String rawInput = "Spent 450 rupees on dinner";
        AiParsedResponseDTO mockAiResp = new AiParsedResponseDTO(new BigDecimal("450.00"), "INR", "Food & Dining", "Restaurant");
        
        when(aiAgentClient.parseText(any())).thenReturn(mockAiResp);
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // When
        TransactionDTO result = expenseService.parseAndSaveExpense(1L, rawInput);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("450.00"), result.getAmount());
        assertEquals("Food & Dining", result.getCategoryName());
        verify(transactionRepository, times(1)).save(any());
    }
}
```

## 3. Postman Collection Verification Checklist
* `POST /api/v1/ai/parse-expense` $\rightarrow$ Expect `200 OK` with JSON fields.
* `POST /api/v1/expenses/parse-and-save` $\rightarrow$ Expect `201 Created` with Transaction ID.
* `POST /api/v1/expenses/webhook/sms` $\rightarrow$ Expect `201 Created`.
* `GET /api/v1/expenses/dashboard-summary` $\rightarrow$ Expect `200 OK` with non-null metrics.