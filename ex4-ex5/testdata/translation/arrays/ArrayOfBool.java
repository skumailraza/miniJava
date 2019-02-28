class ArrayOk {

	public static void main(String[] args) {
		boolean[] arr;
		arr = new boolean[4];
		arr[0] = true;
		arr[1] = false;
		arr[2] = false;
		arr[3] = true;
		if (arr[0]) System.out.println(0); else System.out.println(1);
		if (arr[1]) System.out.println(0); else System.out.println(1);
		if (arr[2]) System.out.println(0); else System.out.println(1);
		if (arr[3]) System.out.println(0); else System.out.println(1);
		System.out.println(arr.length);
	}
}
