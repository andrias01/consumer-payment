package com.co.eatupapi.services.payment.paymentmethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Servicio que expone el contrato fuerte de validación de métodos de pago.
 *
 * <p>En este consumer, la información del método de pago llega como parte del
 * mensaje de comando (campo {@code paymentMethodActive} y {@code paymentMethodId}).
 * Estos métodos centralizan y formalizan todas las validaciones relacionadas con
 * métodos de pago para que {@code CashReceiptCommandHandler} —y cualquier otro
 * servicio futuro— las consuma de forma reutilizable y explícita.
 */
@Service
public class PaymentMethodValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodValidator.class);

    /**
     * Retorna el {@code paymentMethodId} validado si el método de pago está activo.
     *
     * <p>Equivalente a {@code getActivePaymentMethodById}: dado el id y su estado
     * activo (resuelto desde el mensaje), garantiza que el método existe y está activo
     * antes de devolver el identificador.
     *
     * @param paymentMethodId id del método de pago (debe ser no-nulo)
     * @param active          flag de actividad proveniente del mensaje de comando
     * @return el mismo {@code paymentMethodId} si pasa la validación
     * @throws IllegalArgumentException si el id es nulo o el método está inactivo
     */
    public UUID getActivePaymentMethodById(UUID paymentMethodId, Boolean active) {
        requirePaymentMethodId(paymentMethodId);
        if (!Boolean.TRUE.equals(active)) {
            throw new IllegalArgumentException(
                    "Payment method is inactive or not found: " + paymentMethodId);
        }
        log.debug("Payment method validated as active: paymentMethodId={}", paymentMethodId);
        return paymentMethodId;
    }

    /**
     * Valida que el método de pago esté activo; lanza excepción si no lo está.
     *
     * <p>Versión void de {@link #getActivePaymentMethodById} para uso semántico
     * cuando el llamador sólo necesita la garantía de actividad, no el id de retorno.
     *
     * @param paymentMethodId id del método de pago (debe ser no-nulo)
     * @param active          flag de actividad proveniente del mensaje de comando
     * @throws IllegalArgumentException si el id es nulo o el método está inactivo
     */
    public void validateActivePaymentMethod(UUID paymentMethodId, Boolean active) {
        getActivePaymentMethodById(paymentMethodId, active);
    }

    /**
     * Valida que el método de pago esté activo y pertenezca a la sede indicada.
     *
     * <p>Este contrato aplica cuando los métodos de pago están asociados a una sede
     * específica. El campo {@code paymentMethodLocationId} debe viajar en el mensaje
     * de comando para que el consumer pueda compararlo con el {@code locationId} de
     * la operación.
     *
     * @param paymentMethodId         id del método de pago
     * @param active                  flag de actividad proveniente del mensaje
     * @param paymentMethodLocationId sede a la que pertenece el método de pago (puede
     *                                ser {@code null} si el método es global)
     * @param operationLocationId     sede de la operación actual
     * @throws IllegalArgumentException si el método está inactivo o no pertenece a la sede
     */
    public void validatePaymentMethodForLocation(UUID paymentMethodId,
                                                 Boolean active,
                                                 UUID paymentMethodLocationId,
                                                 UUID operationLocationId) {
        validateActivePaymentMethod(paymentMethodId, active);

        if (paymentMethodLocationId != null
                && !Objects.equals(paymentMethodLocationId, operationLocationId)) {
            throw new IllegalArgumentException(
                    "Payment method " + paymentMethodId
                            + " does not belong to location " + operationLocationId);
        }
        log.debug("Payment method validated for location: paymentMethodId={}, locationId={}",
                paymentMethodId, operationLocationId);
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void requirePaymentMethodId(UUID paymentMethodId) {
        if (paymentMethodId == null) {
            throw new IllegalArgumentException("Required field is missing: paymentMethodId");
        }
    }
}
