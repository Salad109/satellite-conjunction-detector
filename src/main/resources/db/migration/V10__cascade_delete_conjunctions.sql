ALTER TABLE conjunction DROP CONSTRAINT conjunction_object1_norad_id_fkey;
ALTER TABLE conjunction DROP CONSTRAINT conjunction_object2_norad_id_fkey;

ALTER TABLE conjunction
    ADD CONSTRAINT conjunction_object1_norad_id_fkey
        FOREIGN KEY (object1_norad_id) REFERENCES satellite (norad_cat_id) ON DELETE CASCADE;
ALTER TABLE conjunction
    ADD CONSTRAINT conjunction_object2_norad_id_fkey
        FOREIGN KEY (object2_norad_id) REFERENCES satellite (norad_cat_id) ON DELETE CASCADE;