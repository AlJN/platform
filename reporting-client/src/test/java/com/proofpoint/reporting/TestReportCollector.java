/*
 * Copyright 2013 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import java.util.Map;

import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public class TestReportCollector
{
    private static final ImmutableMap<String, String> EXPECTED_VERSION_TAGS = ImmutableMap.of("applicationVersion", "1.2", "platformVersion", "platform.1");

    private MinuteBucketIdProvider bucketIdProvider;
    private ReportedBeanRegistry reportedBeanRegistry;
    private ReportSink reportSink;
    private ReportCollector reportCollector;

    @Captor
    ArgumentCaptor<Table<String, Map<String, String>, Object>> tableCaptor;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        bucketIdProvider = mock(MinuteBucketIdProvider.class);
        reportedBeanRegistry = new ReportedBeanRegistry();
        reportSink = mock(ReportQueue.class);
        NodeInfo nodeInfo = new NodeInfo("test-application", "1.2", "platform.1", new NodeConfig().setEnvironment("testing"));
        reportCollector = new ReportCollector(nodeInfo, bucketIdProvider, reportedBeanRegistry, reportSink);
    }

    @Test
    public void testCollection()
            throws Exception
    {
        Object reported = new ReportedObject();
        reportedBeanRegistry.register(reported, ReportedBean.forTarget(reported, bucketIdProvider), false, "TestObject", ImmutableMap.of());

        assertMetricsCollected("TestObject.Metric", ImmutableMap.of());
    }

    @Test
    public void testCollectionApplicationPrefix()
            throws Exception
    {
        Object reported = new ReportedObject();
        reportedBeanRegistry.register(reported, ReportedBean.forTarget(reported, bucketIdProvider), true, "TestObject", ImmutableMap.of("foo", "bar"));

        assertMetricsCollected("TestApplication.TestObject.Metric", ImmutableMap.of("foo", "bar"));
    }

    @Test
    public void testCollectionLegacy()
            throws Exception
    {
        ObjectName objectName = ObjectName.getInstance("com.proofpoint.reporting.test:name=TestObject,foo=bar");
        reportedBeanRegistry.register(ReportedBean.forTarget(new ReportedObject(), bucketIdProvider), objectName);

        assertMetricsCollected("TestObject.Metric", ImmutableMap.of("foo", "bar"));
    }

    private void assertMetricsCollected(String expectedMetricName, Map<String, String> expectedTags)
    {
        when(bucketIdProvider.getLastSystemTimeMillis()).thenReturn(12345L);
        reportCollector.collectData();

        verify(reportSink).report(eq(12345L), tableCaptor.capture());
        verifyNoMoreInteractions(reportSink);

        Table<String, Map<String, String>, Object> table = tableCaptor.getValue();
        assertEquals(table.cellSet(), ImmutableTable.<String, Map<String, String>, Object>builder()
                .put(expectedMetricName, expectedTags, 1)
                .put("ReportCollector.NumMetrics", EXPECTED_VERSION_TAGS, 1)
                .build()
                .cellSet());
    }

    @Test
    public void testUnreportedValues()
            throws Exception
    {
        when(bucketIdProvider.getLastSystemTimeMillis()).thenReturn(12345L);
        Object reported = new Object()
        {
            @Reported
            public double getDoubleMetric()
            {
                return 0;
            }

            @Reported
            public double getNanDouble()
            {
                return Double.NaN;
            }

            @Reported
            public double getInfiniteDouble()
            {
                return Double.NEGATIVE_INFINITY;
            }

            @Reported
            public float getFloatMetric()
            {
                return 0F;
            }

            @Reported
            public float getNanFloat()
            {
                return Float.NaN;
            }

            @Reported
            public float getInfiniteFloat()
            {
                return Float.POSITIVE_INFINITY;
            }

            @Reported
            public long getLongMetric()
            {
                return 0L;
            }

            @Reported
            public long getMaxLongMetric()
            {
                return Long.MAX_VALUE;
            }

            @Reported
            public long getMinLongMetric()
            {
                return Long.MIN_VALUE;
            }

            @Reported
            public int getIntegerMetric()
            {
                return 0;
            }

            @Reported
            public int getMaxIntegerMetric()
            {
                return Integer.MAX_VALUE;
            }

            @Reported
            public int getMinIntegerMetric()
            {
                return Integer.MIN_VALUE;
            }

            @Reported
            public short getShortMetric()
            {
                return 0;
            }

            @Reported
            public short getMaxShortMetric()
            {
                return Short.MAX_VALUE;
            }

            @Reported
            public short getMinShortMetric()
            {
                return Short.MIN_VALUE;
            }

            @Reported
            public byte getByteMetric()
            {
                return 0;
            }

            @Reported
            public byte getMaxByteMetric()
            {
                return Byte.MAX_VALUE;
            }

            @Reported
            public byte getMinByteMetric()
            {
                return Byte.MIN_VALUE;
            }

            @Reported
            public boolean getFalseBooleanMetric()
            {
                return false;
            }

            @Reported
            public Boolean getTrueBooleanMetric()
            {
                return true;
            }

            @Reported
            public Boolean getNullBooleanMetric()
            {
                return null;
            }

            @Reported
            public TestingValue getTestingValueMetric()
            {
                return new TestingValue();
            }

            @Reported
            public Integer getNullMetric()
            {
                return null;
            }

            @Reported
            public int getExceptionMetric()
            {
                throw new UnsupportedOperationException();
            }
        };
        reportedBeanRegistry.register(reported, ReportedBean.forTarget(reported, bucketIdProvider), false, "TestObject", ImmutableMap.of());

        reportCollector.collectData();

        verify(reportSink).report(eq(12345L), tableCaptor.capture());
        verifyNoMoreInteractions(reportSink);

        Table<String, Map<String, String>, Object> table = tableCaptor.getValue();
        assertEqualsIgnoreOrder(table.cellSet(), ImmutableTable.<String, Map<String, String>, Object>builder()
                .put("TestObject.DoubleMetric", ImmutableMap.of(), 0.0)
                .put("TestObject.FloatMetric", ImmutableMap.of(), 0F)
                .put("TestObject.LongMetric", ImmutableMap.of(), 0L)
                .put("TestObject.IntegerMetric", ImmutableMap.of(), 0)
                .put("TestObject.ShortMetric", ImmutableMap.of(), (short) 0)
                .put("TestObject.ByteMetric", ImmutableMap.of(), (byte) 0)
                .put("TestObject.MaxByteMetric", ImmutableMap.of(), Byte.MAX_VALUE)
                .put("TestObject.MinByteMetric", ImmutableMap.of(), Byte.MIN_VALUE)
                .put("TestObject.FalseBooleanMetric", ImmutableMap.of(), 0)
                .put("TestObject.TrueBooleanMetric", ImmutableMap.of(), 1)
                .put("TestObject.TestingValueMetric", ImmutableMap.of(), "testing toString value")
                .put("ReportCollector.NumMetrics", EXPECTED_VERSION_TAGS, 11)
                .build()
                .cellSet());
    }

    private static class TestingValue
    {
        @Override
        public String toString()
        {
            return "testing toString value";
        }
    }

    private static class ReportedObject
    {
        private int metric = 0;

        @Reported
        public int getMetric()
        {
            return ++metric;
        }
    }
}
