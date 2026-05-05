package com.co.eatupapi.messaging.payment.cashreceipt;

import com.co.eatupapi.services.payment.cashreceipt.CashReceiptCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class CashReceiptMessageListener {

    private static final Logger log = LoggerFactory.getLogger(CashReceiptMessageListener.class);

    private final CashReceiptCommandHandler commandHandler;

    public CashReceiptMessageListener(CashReceiptCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    //@Headers Map<String, Object> headers
    //headers.forEach((k, v) -> System.out.println(k + ": " + v));
    //A todo el mensaje System.out.println(message.getMessageProperties().getHeaders());
    @RabbitListener(queues = "${rabbitmq.queue.payment.cashreceipt.create}")
    public void onCreate(CashReceiptCreateMessage message,
                         @Header("hola") String hola) {
        try {
            commandHandler.handleCreate(message);
            System.out.println("Header hola: " + hola);
            log.info(
                    "Processed cashreceipt create message: locationId={}, invoiceId={}, paymentMethodId={}, Mensaje del head con hola:{}",
                    message != null ? message.getLocationId() : null,
                    message != null ? message.getInvoiceId() : null,
                    message != null ? message.getPaymentMethodId() : null,
                    hola
            );
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Rejected cashreceipt create message due to validation/business error: {} | payload={}",
                    ex.getMessage(),
                    message
            );
        } catch (Exception ex) {
            log.error(
                    "Failed processing cashreceipt create message. Error={} | payload={}",
                    ex.getMessage(),
                    message
            );
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
        } catch (Exception ex) {
            log.error(
                    "Failed processing cashreceipt cancel message. Error={} | payload={}",
                    ex.getMessage(),
                    message
            );
        }
    }
}
