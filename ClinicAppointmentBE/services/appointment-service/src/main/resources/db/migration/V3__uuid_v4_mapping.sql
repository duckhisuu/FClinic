-- V1/V2 are immutable. Provision authoritative external mappings here before V4 on legacy data.
CREATE TABLE appointment_uuid_v4_mapping (
 resource_type varchar(40) NOT NULL CHECK(resource_type IN
  ('appointment','appointment-history','operation-result','event','reminder-publication','patient','doctor','slot','user')),
 old_id uuid NOT NULL,
 new_id uuid NOT NULL CHECK(new_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
 PRIMARY KEY(resource_type,old_id), UNIQUE(resource_type,new_id), CHECK(old_id<>new_id)
);
