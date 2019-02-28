class Main {
	public static void main(String[] args){
		A a;
		B b;
		b = new B();
		a = b;
		if (a == b) {
			System.out.println(12);
		} else {
			System.out.println(13);
		}
	}
}


class A {

}

class B  extends A {
	

}