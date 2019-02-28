class ArrayOk {

	public static void main(String[] args) {
		C[] arr;
		arr = new C[4];
		arr[0] = new C();
		arr[1] = arr[0];
		arr[2] = new C();
		arr[3] = arr[1];
		System.out.println(arr[0].get());
		System.out.println(arr[1].get());
		System.out.println(arr[2].get());
		System.out.println(arr[3].get());
		System.out.println(arr.length);
	}
}

class C {
	int x;

	int get() {
		this.x = this.x + 1;
		return x;
	}
}
