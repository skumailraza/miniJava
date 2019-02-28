class ArrayOk {

	public static void main(String[] args) {
		int[][][] arr;
		arr = new int[2][][];
		int[] x;
		arr[1] = new int[2][];
		arr[1][1] = new int[2];
		arr[1][1][1] = 1;
		System.out.println(arr[1][1][0]);
		System.out.println(arr[1][1][1]);
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
