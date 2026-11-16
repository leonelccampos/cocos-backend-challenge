CREATE INDEX orders_userid_idx ON orders (userid);

CREATE INDEX orders_instrumentid_idx ON orders (instrumentid);

CREATE INDEX marketdata_instrumentid_date_id_idx
    ON marketdata (instrumentid, date DESC, id DESC)
    WHERE date IS NOT NULL;
