package com.github.ryanbrainard.richsobjects;

import com.github.ryanbrainard.richsobjects.api.model.SObjectDescription;
import sun.misc.BASE64Decoder;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Ryan Brainard
 */
public class ImmutableRichSObject implements RichSObject {

    private static final BASE64Decoder BASE_64_DECODER = new BASE64Decoder();
    private static final SimpleDateFormat API_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final transient RichSObjectsService service;
    private final SObjectDescription metadata;
    private final Map<String, Object> record;
    private final Map<String, SObjectDescription.Field> indexedFieldMetadata;
    private final List<SObjectDescription.Field> sortedFieldMetadata;

    ImmutableRichSObject(RichSObjectsService service, SObjectDescription metadata, Map<String, ?> record) {
        this(service, metadata, record, ORDER_BY_FIELD_LABEL);
    }

    ImmutableRichSObject(RichSObjectsService service, SObjectDescription metadata, Map<String, ?> record, Comparator<SObjectDescription.Field> sortFieldsBy) {
        this.service = service;
        this.metadata = metadata;

        final Map<String, Object> tmpRecord = new HashMap<String, Object>(record.size());
        for (Map.Entry<String, ?> field : record.entrySet()) {
            tmpRecord.put(field.getKey().toUpperCase(), field.getValue());
        }
        this.record = Collections.unmodifiableMap(tmpRecord);

        final Map<String,SObjectDescription.Field> tmpIndexedFieldMetadata = new HashMap<String,SObjectDescription.Field>(metadata.getFields().size());
        for (SObjectDescription.Field f : metadata.getFields()) {
            tmpIndexedFieldMetadata.put(f.getName().toUpperCase(), f);
        }
        this.indexedFieldMetadata = Collections.unmodifiableMap(tmpIndexedFieldMetadata);

        final List<SObjectDescription.Field> tmpSortedFieldMetadata = new ArrayList<SObjectDescription.Field>(metadata.getFields());
        Collections.sort(tmpSortedFieldMetadata, sortFieldsBy);
        this.sortedFieldMetadata = Collections.unmodifiableList(tmpSortedFieldMetadata);
    }

    @Override
    public SObjectDescription getMetadata() {
        return metadata;
    }

    @Override
    public RichField get(String fieldName) {
        return new ImmutableRichField(fieldName.toUpperCase());
    }

    @Override
    public Iterator<RichField> iterator() {
        return getFields();
    }

    @Override
    public Iterator<RichField> getFields() {
        return new Iterator<RichField>() {
            private final Iterator<SObjectDescription.Field> fieldIterator = sortedFieldMetadata.iterator();

            @Override
            public boolean hasNext() {
                return fieldIterator.hasNext();
            }

            @Override
            public RichField next() {
                return get(fieldIterator.next().getName());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Fields cannot be removed");
            }
        };
    }

    public class ImmutableRichField implements RichField {

        private final String fieldName;

        public ImmutableRichField(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        public RichSObject getParent() {
            return ImmutableRichSObject.this;
        }

        @Override
        public SObjectDescription.Field getMetadata() {
            return indexedFieldMetadata.get(fieldName);
        }

        @Override
        public Object getValue() {
            if (!record.containsKey(fieldName)) {
                final String qualifiedFieldName = getParent().getMetadata().getName() + "." + fieldName;
                if (!indexedFieldMetadata.containsKey(fieldName)) {
                    throw new IllegalArgumentException("No such field: " + qualifiedFieldName);
                } else {
                    throw new IllegalStateException("Field not queried or value not supplied: " + qualifiedFieldName);
                }
            }

            return record.get(fieldName);
        }
        
        @Override
        public Object asAny() {
            final String soapType = getMetadata().getSoapType();

            if ("xsd:string".equals(soapType) || "tns:ID".equals(soapType)) {
                return asString();
            } else if ("xsd:boolean".equals(soapType)) {
                return asBoolean();
            } else if ("xsd:int".equals(soapType)) {
                return asInteger();
            } else if ("xsd:double".equals(soapType)) {
                return asDouble();
            } else if ("xsd:date".equals(soapType) || "xsd:dateTime".equals(soapType)) {
                return asDate();
            } else if ("xsd:base64Binary".equals(soapType)) {
                return asBytes();
            } else {
                return getValue();
            }
        }

        @Override
        public String asString() {
            if (getValue() == null) {
                return null;
            }
            
            return getValue().toString();
        }

        @Override
        public Boolean asBoolean() {
            if (getValue() instanceof Boolean) {
                return (Boolean) getValue();
            } else {
                return Boolean.valueOf(asString());
            }
        }

        @Override
        public Integer asInteger() {
            if (getValue() instanceof Integer) {
                return (Integer) getValue();
            } else {
                return Integer.valueOf(asString());
            }
        }

        @Override
        public Double asDouble() {
            if (getValue() instanceof Double) {
                return (Double) getValue();
            } else {
                return Double.valueOf(asString());
            }
        }

        @Override
        public Date asDate() {
            if (getValue() == null) {
                return null;
            }
            
            try {
                return API_DATE_FORMAT.parse(asString());
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] asBytes() {
            if (getValue() == null) {
                return new byte[0];
            }

            try {
                return BASE_64_DECODER.decodeBuffer(service.getApiClient().getRawBase64Content(asString()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Comparator<SObjectDescription.Field> ORDER_BY_FIELD_LABEL = new Comparator<SObjectDescription.Field>() {
        @Override
        public int compare(SObjectDescription.Field o1, SObjectDescription.Field o2) {
            return o1.getLabel().compareTo(o2.getLabel());
        }
    };
}
