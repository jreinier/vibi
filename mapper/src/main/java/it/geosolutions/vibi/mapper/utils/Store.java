package it.geosolutions.vibi.mapper.utils;

import org.geotools.data.*;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.FilterFactory2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Store {

    private final static FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());

    public static FeatureStore<SimpleFeatureType, SimpleFeature> getFeatureStore(DataStore store, String featureTypeName) {
        try {
            return (FeatureStore<SimpleFeatureType, SimpleFeature>) store.getFeatureSource(featureTypeName);
        } catch (Exception exception) {
            throw new RuntimeException(String.format("Error obtaining feature store of type '%s'.", featureTypeName), exception);
        }
    }

    public static void persistFeature(DataStore store, SimpleFeature simpleFeature) {
        SimpleFeature foundFeature = find(store, simpleFeature);
        if (foundFeature == null) {
            create(store, simpleFeature);
        } else {
            update(store, simpleFeature);
        }
    }

    private static SimpleFeature find(DataStore store, final SimpleFeature simpleFeature) {
        return new InTransaction(store, simpleFeature.getFeatureType().getTypeName(), "find") {

            SimpleFeature feature;

            @Override
            public void doWork(FeatureStore<SimpleFeatureType, SimpleFeature> featureStore) throws Exception {
                FeatureCollection<SimpleFeatureType, SimpleFeature> features =
                        featureStore.getFeatures(filterFactory.id(simpleFeature.getIdentifier()));
                Validations.checkCondition(features.size() <= 1, "To much features found.");
                if (!features.isEmpty()) {
                    feature = features.features().next();
                }
            }
        }.feature;
    }

    private static void create(DataStore store, final SimpleFeature simpleFeature) {
        new InTransaction(store, simpleFeature.getFeatureType().getTypeName(), "create") {

            @Override
            public void doWork(FeatureStore<SimpleFeatureType, SimpleFeature> featureStore) throws Exception {
                featureStore.addFeatures(DataUtilities.collection(simpleFeature));
            }
        };
    }

    private static void update(DataStore store, final SimpleFeature simpleFeature) {

        final List<Property> properties = filterInvalidProperties(simpleFeature.getProperties());

        if(properties.isEmpty()) {
            return;
        }

        new InTransaction(store, simpleFeature.getFeatureType().getTypeName(), "update") {

            @Override
            public void doWork(FeatureStore<SimpleFeatureType, SimpleFeature> featureStore) throws Exception {
                Name[] names = new Name[properties.size()];
                Object[] values = new Object[properties.size()];
                int i = 0;
                for (Property property : properties) {
                    names[i] = property.getName();
                    values[i] = property.getValue();
                    i++;
                }
                featureStore.modifyFeatures(names, values, filterFactory.id(simpleFeature.getIdentifier()));
            }
        };
    }

    private static List<Property> filterInvalidProperties(Collection<Property> properties) {
        List<Property> filteredProperties = new ArrayList<>();
        for (Property property : properties) {
            if (!(property.getName().toString().equals("") && property.getValue() == null)) {
                filteredProperties.add(property);
            }
        }
        return filteredProperties;
    }

    private static abstract class InTransaction {

        public InTransaction(DataStore store, String featureTypeName, String handle) {
            Transaction transaction = new DefaultTransaction(handle);
            FeatureStore<SimpleFeatureType, SimpleFeature> featureStore = getFeatureStore(store, featureTypeName);
            featureStore.setTransaction(transaction);
            try {
                doWork(featureStore);
                transaction.commit();
            } catch (Exception exception) {
                throw new RuntimeException(String.format("Error persisting feature of type '%s'.", featureTypeName), exception);
            } finally {
                try {
                    transaction.close();
                } catch (Exception exception) {
                    throw new RuntimeException(String.format("Error closing transaction when persisting feature of type '%s'.",
                            featureTypeName), exception);
                }
            }
        }

        public abstract void doWork(FeatureStore<SimpleFeatureType, SimpleFeature> featureStore) throws Exception;
    }
}