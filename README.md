# 🍽️ EatUpAPI — Consumer Payment Service

Microservicio encargado de procesar eventos de escritura del módulo de pagos dentro de la arquitectura distribuida de EatUpAPI.

---

## 🚀 Descripción General

`consumer_payment` es un microservicio basado en eventos que consume mensajes desde RabbitMQ para ejecutar operaciones de persistencia sobre recibos de caja (`cash_receipts`).

Este servicio actúa como el lado consumidor dentro de una arquitectura desacoplada, permitiendo escalar y aislar la lógica de escritura del sistema.

---

## 🧩 Arquitectura

Producer (copiaPruebas)
        ↓
   RabbitMQ (Exchange)
        ↓
     Queues
        ↓
consumer_payment (Listener → Handler → Repository → DB)

---

## ⚙️ Configuración Base

spring.application.name=consumer_payment  
server.port=8084  

- Importa configuración desde `payment.properties`
- Conexión a PostgreSQL y RabbitMQ
- Variables externas vía `.env`

---

## 🧠 Componentes Principales

### 🔹 Configuración RabbitMQ
- PaymentRabbitMQConfig
- Define Exchanges, Queues y Bindings

### 🔹 Mensajes
- CashReceiptCreateMessage
- CashReceiptCancelMessage

### 🔹 Listener
- CashReceiptMessageListener
- Escucha colas y delega al handler

### 🔹 Lógica de Negocio
- CashReceiptCommandHandler

### 🔹 Persistencia
- CashReceipt
- CashReceiptStatus
- CashReceiptRepository

---

## 📬 Colas y Eventos

### 🟢 Crear Recibo
Queue: rabbitmq.queue.payment.cashreceipt.create  
Routing Key: rabbitmq.routing-key.payment.cashreceipt.create  
Acción: Crear un nuevo recibo  

### 🔴 Cancelar Recibo
Queue: rabbitmq.queue.payment.cashreceipt.cancel  
Routing Key: rabbitmq.routing-key.payment.cashreceipt.cancel  
Acción: Marcar recibo como CANCELLED  

---

## 🔄 Operaciones Soportadas

### ✅ Create
- Crea nueva entidad CashReceipt
- Estado inicial: PAID
- Persiste en base de datos

### ❌ Cancel
- Busca por receiptId
- Valida locationId
- Idempotencia básica
- Estado → CANCELLED
- Fecha → cancelledAt

---

## 📏 Convenciones

- Una cola por operación
- Routing keys: <module>.<service>.<operation>
- Mensajes JSON (Jackson)
- Listener desacoplado del handler

---

## ⚠️ Consideraciones

- No existe DELETE para cashreceipt
- Si se implementa:
  - Nueva cola
  - Nueva routing key
  - Nuevo handler

---

## 🐳 Ejecución con Docker (RabbitMQ)

docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin rabbitmq:3-management

Panel: http://localhost:15672

---

## 🧪 Flujo

1. Producer publica mensaje
2. RabbitMQ enruta
3. Listener consume
4. Handler procesa
5. Repository persiste

---

## 🏫 Información Académica

Proyecto: EatUpAPI  
Materia: Software 3  
Semestre: 2026-1  
Institución: Universidad Católica de Oriente  
Docente: Juan Pablo Noreña Blandón  

---

## 👨‍💻 Autores

Andrés Felipe Vélez Alcaraz  
José Alejandro Valencia Henao  

---

## 💡 Nota Final

Este microservicio permite:

- Escalabilidad  
- Desacoplamiento  
- Procesamiento asíncrono  
- Resiliencia  
