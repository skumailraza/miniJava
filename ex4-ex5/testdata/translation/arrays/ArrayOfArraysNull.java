class ArrayOk {

	public static void main(String[] args) {
		int[][] arr;
		arr = new int[2][];
		int[] x;
		if (arr[1] == null)
			System.out.println(1);
		else
			System.out.println(2);
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
