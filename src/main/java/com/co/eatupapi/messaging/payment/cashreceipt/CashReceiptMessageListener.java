package com.co.eatupapi.messaging.payment.cashreceipt;

import com.co.eatupapi.services.payment.cashreceipt.CashReceiptCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CashReceiptMessageListener {

    private static final Logger log = LoggerFactory.getLogger(CashReceiptMessageListener.class);

    private final CashReceiptCommandHandler commandHandler;

    public CashReceiptMessageListener(CashReceiptCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment.cashreceipt.create}")
    public void onCreate(CashReceiptCreateMessage message) {
        try {
            commandHandler.handleCreate(message);
            log.info(
                    "Processed cashreceipt create message: locationId={}, invoiceId={}, paymentMethodId={}",
                    message != null ? message.getLocationId() : null,
                    message != null ? message.getInvoiceId() : null,
                    message != null ? message.getPaymentMethodId() : null
            );
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Rejected cashreceipt create message due to validation/business error: {} | payload={}",
                    ex.getMessage(),
                    message
            );
            System.err.printf(
                    "[consumer_payment][cashreceipt.create][VALIDATION_ERROR] %s | payload=%s%n",
                    ex.getMessage(),
                    message
            );
            ex.printStackTrace();
        } catch (Exception ex) {
            log.error(
                    "Failed processing cashreceipt create message. Error={} | payload={}",
                    ex.getMessage(),
                    message
            );
            System.err.printf(
                    "[consumer_payment][cashreceipt.create][UNEXPECTED_ERROR] %s | payload=%s%n",
                    ex.getMessage(),
                    message
            );
            ex.printStackTrace();
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment.cashreceipt.cancel}")
    public void onCancel(CashReceiptCancelMessage message) {
        try {
            commandHandler.handleCancel(message);
            log.info(
                    "Processed cashreceipt cancel message: locationId={}, receiptId={}",
                    message != null ? message.getLocationId() : null,
                    message != null ? message.getReceiptId() : null
            );
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Rejected cashreceipt cancel message due to validation/business error: {} | payload={}",
                    ex.getMessage(),
                    message
            );
            System.err.printf(
                    "[consumer_payment][cashreceipt.cancel][VALIDATION_ERROR] %s | payload=%s%n",
                    ex.getMessage(),
                    message
            );
            ex.printStackTrace();
        } catch (Exception ex) {
            log.error(
                    "Failed processing cashreceipt cancel message. Error={} | payload={}",
                    ex.getMessage(),
                    message
            );
            System.err.printf(
                    "[consumer_payment][cashreceipt.cancel][UNEXPECTED_ERROR] %s | payload=%s%n",
                    ex.getMessage(),
                    message
            );
            ex.printStackTrace();
        }
    }
}
