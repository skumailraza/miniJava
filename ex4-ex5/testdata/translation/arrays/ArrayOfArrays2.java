class ArrayOk {

	public static void main(String[] args) {
		int[][] arr;
		arr = new int[2][];
		int[] x;
		x = new int[2];
		arr[0] = x;
		arr[1] = x;
		arr[0][0] = 1;
		arr[0][1] = 1;
		arr[1][0] = 1;
		arr[1][1] = 1;
		System.out.println(arr[0][0]);
		System.out.println(arr[0][1]);
		System.out.println(arr[1][0]);
		System.out.println(arr[1][1]);
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
