package com.co.eatupapi.messaging.payment.invoice;

import com.co.eatupapi.services.payment.invoice.InvoiceCommandHandler;
import com.co.eatupapi.services.payment.invoice.InvoiceMessageValidationException;
import com.co.eatupapi.services.payment.invoice.InvoiceProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceMessageListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceMessageListener.class);

    private final InvoiceCommandHandler commandHandler;

    public InvoiceMessageListener(InvoiceCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment.invoice.create}")
    public void handleCreate(InvoiceCreateMessage message) {
        try {
            commandHandler.handleCreate(message);
            log.info("Processed invoice create message: invoiceId={}, invoiceNumber={}, locationId={}",
                    message != null ? message.getInvoiceId() : null,
                    message != null ? message.getInvoiceNumber() : null,
                    message != null ? message.getLocationId() : null);
        } catch (InvoiceMessageValidationException ex) {
            log.warn("Rejected invoice create message due to validation error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (InvoiceProcessingException ex) {
            log.warn("Rejected invoice create message due to business error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (Exception ex) {
            log.error("Failed processing invoice create message. Error={} | payload={}",
                    ex.getMessage(), message);
            throw ex;
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment.invoice.cancel}")
    public void handleCancel(InvoiceCancelMessage message) {
        try {
            commandHandler.handleCancel(message);
            log.info("Processed invoice cancel message: invoiceId={}, locationId={}",
                    message != null ? message.getInvoiceId() : null,
                    message != null ? message.getLocationId() : null);
        } catch (InvoiceMessageValidationException ex) {
            log.warn("Rejected invoice cancel message due to validation error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (InvoiceProcessingException ex) {
            log.warn("Rejected invoice cancel message due to business error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (Exception ex) {
            log.error("Failed processing invoice cancel message. Error={} | payload={}",
                    ex.getMessage(), message);
            throw ex;
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment.invoice.mark-paid}")
    public void handleMarkPaid(InvoiceMarkPaidMessage message) {
        try {
            commandHandler.handleMarkPaid(message);
            log.info("Processed invoice mark-paid message: invoiceId={}, locationId={}",
                    message != null ? message.getInvoiceId() : null,
                    message != null ? message.getLocationId() : null);
        } catch (InvoiceMessageValidationException ex) {
            log.warn("Rejected invoice mark-paid message due to validation error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (InvoiceProcessingException ex) {
            log.warn("Rejected invoice mark-paid message due to business error: {} | payload={}",
                    ex.getMessage(), message);
        } catch (Exception ex) {
            log.error("Failed processing invoice mark-paid message. Error={} | payload={}",
                    ex.getMessage(), message);
            throw ex;
        }
    }
}
