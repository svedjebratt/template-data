package com.molnmyra;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Magnus
 */
public class TemplateDataTest {

	private Source source;

	@Before
	public void before() {
		source = new Source();
	}

	@Test
	public void testDirectTransfer() {
		Data1 data1 = TemplateData.create(source, "template1", Data1.class);
		assertEquals("Bulle", data1.f1);
		assertNull(data1.f2);
		assertNull(data1.fdata);
	}

	@Test
	public void testMethodInvocationTransfer() {
		Data2 data2 = TemplateData.create(source, Data2.class);
		assertEquals(666, data2.f1.intValue());
		assertEquals(123, data2.f2.intValue());
		assertNull(data2.f3);
	}

	@Test
	public void testNested() {
		Data3 data3 = TemplateData.create(source, "kvida", Data3.class);
		assertEquals(Double.valueOf(1.23d), Double.valueOf(data3.fdata.hungra));
		assertEquals("Vilja", data3.fdata.vilja);
	}

	@Test
	public void testParam() {
		Data1 param = new Data1();
		Data3 param3 = new Data3();
		param.f1 = "param f1";
		Data2 data2 = TemplateData.create(source, Data2.class, param, param3);
		assertEquals("param f1", data2.f3);
		data2 = TemplateData.create(source, Data2.class, param);
		assertEquals("param f1", data2.f3);
	}

	@Test
	public void testMethodsOnDestination() {
		Data5 data5 = TemplateData.create(source, Data5.class);
		assertEquals("lapin", data5.latjo);
		assertNull(data5.lajban);
		assertEquals(Integer.valueOf(666), data5.ynnest);
		assertTrue("source2.hungra is less than 2", data5.fdata.ondsam);
		assertEquals("Vilja", data5.fdata.vilja);
	}

	@Test
	public void testPropertyNameChange() {
		Data7 data7 = TemplateData.create(source, Data7.class);
		assertEquals("Bulle", data7.notF1);
		assertEquals("Bulle", data7.otherF1);
		assertEquals("Vilja", data7.data8.notVilja);
	}

	@Test
	public void testPrimitives() {
		Data9 data9 = TemplateData.create(source, "primitive", Data9.class);
		assertTrue(data9.fx);
		assertEquals(Integer.valueOf(123), data9.f2);
		assertTrue(data9.fdata.sant);
	}

	@Test
	public void testGenericLists() {
		Source3 source3 = new Source3();
		source3.source2sOther = new ArrayList<>();
		Source2 source2 = new Source2();
		source3.source2sOther.add(source2);
		source2 = new Source2();
		source2.sant = false;
		source3.source2sOther.add(source2);

		source3.myBooleans = new ArrayList<>();
		source3.myBooleans.add(true);
		source3.myBooleans.add(false);
		source3.myBooleans.add(true);
		source3.myBooleans.add(false);

		Data11 data11 = TemplateData.create(source3, Data11.class);
		assertEquals(2, data11.source2s.size());
		assertTrue(data11.source2s.get(0).sant);
		assertFalse(data11.source2s.get(1).sant);

		assertNull(data11.myIntegers);
		assertEquals(4, data11.myBooleans.size());
		assertTrue(data11.myBooleans.get(0));
		assertFalse(data11.myBooleans.get(1));
		assertTrue(data11.myBooleans.get(2));
		assertFalse(data11.myBooleans.get(3));

	}

	// -------------------- Destination objects ------------------------

	@TemplateEntity(@Template(name = "template1", fields = {"f1", "f2"}))
	public static class Data1 {
		public String f1;
		public String f2;
		public Data2 fdata;
	}
	@TemplateEntity
	public static class Data2 {
		public Integer f1;
		public Integer f2;
		public String f3;
	}
	@TemplateEntity(@Template(name = "kvida", fields = {"fdata"}))
	public static class Data3 {
		public Data4 fdata;
	}
	@TemplateEntity(@Template(name = "kvida", fields = {"hungra", "vilja"}))
	public static class Data4 {
		public String vilja;
		public double hungra;
	}

	@TemplateEntity
	public static class Data5 {
		public String latjo;
		public String lajban;
		public Integer ynnest;
		public Data6 fdata;

		public Integer _ynnest(Source s) {
			return s.f1();
		}
	}

	@TemplateEntity
	public static class Data6 {
		public Boolean ondsam;
		public String vilja;

		public static Boolean _ondsam() throws IllegalAccessException {
			throw new IllegalAccessException("This method is not supposed to be called");
		}

		public static Boolean _ondsam(Source2 s) {
			return s.hungra < 2;
		}
	}

	@TemplateEntity
	public static class Data7 {
		@Property("f1")
		public String notF1;

		@Property("myOwnF1")
		public String otherF1;

		public String _myOwnF1(Source s) {
			return s.f1;
		}

		@Property("f1")
		public Integer intF1;

		@Property("fdata")
		public Data8 data8;
	}

	@TemplateEntity
	public static class Data8 {
		@Property("vilja")
		public String notVilja;
	}

	@TemplateEntity(
			@Template(name = "primitive", fields = {"fx", "f2", "fdata"})
	)
	public static class Data9 {
		public Boolean fx;

		public Integer f2;

		public Data10 fdata;
	}

	@TemplateEntity(
			@Template(name = "primitive", fields = "sant")
	)
	public static class Data10 {
		public Boolean sant;
	}

	@TemplateEntity
	public static class Data11 {
		@Property("source2sOther")
		public List<Data10> source2s;

		@Property("myBooleans")
		public List<Integer> myIntegers;

		public List<Boolean> myBooleans;
	}

	// -------- Sources ---------

	public static class Source {
		public String f1 = "Bulle";
		public int f2 = 123;
		public boolean fx = true;
		public Source2 fdata = new Source2();
		public String latjo = "lapin";

		public Integer f1() {
			return 666;
		}

		public String _f3(Data1 data, Data3 data3) {
			return data != null ? data.f1 : null;
		}
	}

	public static class Source2 {
		public String vilja = "Vilja";
		public int flyta = 332;
		public Double hungra = 1.23d;
		public boolean sant = true;
	}

	public static class Source3 {
		public List<Source2> source2sOther;

		public List<Boolean> myBooleans;
	}
}
