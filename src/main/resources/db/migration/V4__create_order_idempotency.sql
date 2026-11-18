CREATE TABLE order_idempotency (
  userid INT NOT NULL,
  idempotencykey VARCHAR(128) NOT NULL,
  requestfingerprint CHAR(64) NOT NULL,
  responsestatus SMALLINT NOT NULL,
  responsebody JSONB NOT NULL,
  orderid INT NOT NULL,
  PRIMARY KEY (userid, idempotencykey),
  FOREIGN KEY (userid) REFERENCES users(id),
  FOREIGN KEY (orderid) REFERENCES orders(id)
);
